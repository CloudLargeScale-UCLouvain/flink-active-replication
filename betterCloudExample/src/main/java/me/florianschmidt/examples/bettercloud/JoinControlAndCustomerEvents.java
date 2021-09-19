package me.florianschmidt.examples.bettercloud;

import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.dropwizard.metrics.DropwizardHistogramWrapper;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.Histogram;
import org.apache.flink.streaming.api.functions.co.RichCoFlatMapFunction;
import org.apache.flink.util.Collector;

import com.codahale.metrics.SlidingTimeWindowReservoir;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * This JoinControlAndCustomerEvents is a {@link RichCoFlatMapFunction} that takes
 * {@link ControlEvent}s and {@link CustomerEvent}s and outputs
 * {@link FilteredEvent}.
 * <p>
 * For each incoming ControlEvent, the JoinControlAndCustomerEvents verifies that it is
 * not a duplicate of an already existing ControlEvent, and if not adds it
 * to the list of control events.
 * <p>
 * For each incoming CustomerEvent, the JoinControlAndCustomerEvents filters for all control
 * events where their customerEventId matches the customerEventId of the incoming customerEvent
 * and emits a FilteredEvent with those control events and the customer event
 */
public class JoinControlAndCustomerEvents extends RichCoFlatMapFunction<ControlEvent, CustomerEvent, FilteredEvent> {

	private ListState<ControlEvent> controls;
	private Counter rulesSentForEvaluation;
	private Counter numRules;
	private Histogram timeToRuleEffective;

	@Override
	public void open(Configuration parameters) {
		ListStateDescriptor<ControlEvent> stateDescriptor
				= new ListStateDescriptor<>("control-events", ControlEvent.class);
		this.controls = getRuntimeContext().getListState(stateDescriptor);
		this.rulesSentForEvaluation = getRuntimeContext().getMetricGroup().counter("rulesSentForEval");
		this.numRules = getRuntimeContext().getMetricGroup().counter("numRulesStored");

		this.timeToRuleEffective = getRuntimeContext().getMetricGroup().histogram(
				"time-to-rule-effective",
				new DropwizardHistogramWrapper(new com.codahale.metrics.Histogram(new SlidingTimeWindowReservoir(10, TimeUnit.SECONDS)))
		);
	}

	@Override
	public void flatMap1(ControlEvent inputEvent, Collector<FilteredEvent> out) throws Exception {

		this.timeToRuleEffective.update(System.currentTimeMillis() - inputEvent.createdAt);

		for (ControlEvent c : controls.get()) {
			if (!c.customerId.equals(inputEvent.customerId)) {
				throw new RuntimeException("Something went wrong, this should always be keyed by customerId");
			} else if (c.alertId.equals(inputEvent.alertId)) {
				// its a duplicate
				System.out.printf("Received duplicate control event with customerId=%s and alertId=%s%n", inputEvent.customerId, inputEvent.alertId);
				return;
			}
		}

		controls.add(inputEvent);
		this.numRules.inc();
	}

	@Override
	public void flatMap2(CustomerEvent value, Collector<FilteredEvent> out) throws Exception {
		List<ControlEvent> controlEvents = Lists.newArrayList(this.controls.get());
		if (controlEvents.size() > 0) {
			this.rulesSentForEvaluation.inc();
			out.collect(new FilteredEvent(value, controlEvents));
		}
	}
}
