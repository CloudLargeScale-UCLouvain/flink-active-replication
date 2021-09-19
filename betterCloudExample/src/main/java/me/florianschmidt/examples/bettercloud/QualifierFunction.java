package me.florianschmidt.examples.bettercloud;

import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.util.Collector;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import me.florianschmidt.examples.bettercloud.rulesengine.Rule;

import java.util.List;

/**
 * The {@link QualifierFunction} is responsible for executing the pre-matched queries,
 * which are stored in the FilteredEvent::controls against the event stored in
 * FilteredEvent::event::payload
 * <p>
 * A query could for example be "$.some.nested.array[1].value > 10"
 */
public class QualifierFunction implements FlatMapFunction<FilteredEvent, QualifiedEvent> {

	@Override
	public void flatMap(FilteredEvent value, Collector<QualifiedEvent> out) throws Exception {

		// This is a very toned down example, it only matches on if the path actually exists
		// In production you would want to compare it to something...
		String payload = value.event.payload;
		DocumentContext ctx = JsonPath.parse(payload);
		List<ControlEvent> controlEvents = value.controls;

		for (ControlEvent controlEvent : controlEvents) {
			boolean allTrue = true;
			for (Rule r : controlEvent.rules) {
				String actualValue = ctx.read(r.path);
				if (!r.evaluate(actualValue)) {
					allTrue = false;
					break;
				}
			}
			System.out.printf("Alert '%s' fired for customer '%s' : %b %n ", controlEvent.alertName, value.event.customerId, allTrue);

			if (allTrue) {
				out.collect(new QualifiedEvent(value.event, controlEvent, 0));
			}
		}
	}
}
