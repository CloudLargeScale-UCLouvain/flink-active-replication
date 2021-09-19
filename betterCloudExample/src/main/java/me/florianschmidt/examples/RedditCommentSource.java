package me.florianschmidt.examples;

import org.apache.flink.streaming.api.functions.source.SourceFunction;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import me.florianschmidt.examples.bettercloud.CustomerEvent;
import me.florianschmidt.examples.bettercloud.Optional;
import org.json.JSONArray;

public class RedditCommentSource implements SourceFunction<Optional<CustomerEvent>> {


	private volatile boolean isRunning = true;

	@Override
	public void run(SourceContext<Optional<CustomerEvent>> ctx) throws Exception {
		while (this.isRunning) {

			try {
				HttpResponse<JsonNode> response = Unirest.get("https://www.reddit.com/r/all/comments/.json?limit=100")
						.header("User-Agent", "Yet another reddit client")
						.header("From", "florian.schmidt.1994@icloud.com")
						.asJson();

				JSONArray comments = response.getBody()
						.getObject()
						.getJSONObject("data")
						.getJSONArray("children");

				for (Object comment : comments) {
					String commentString = comment.toString();
					CustomerEvent c = new CustomerEvent();
					c.customerId = "justOneBigCustomer";
					c.payload = commentString;
					synchronized (ctx.getCheckpointLock()) {
						ctx.collect(Optional.of(c));
					}
				}

				Thread.sleep(2000);

			} catch (UnirestException e) {
				System.err.println("Failed to query reddit comments");
			}
		}
	}

	@Override
	public void cancel() {
		this.isRunning = false;
	}
}
