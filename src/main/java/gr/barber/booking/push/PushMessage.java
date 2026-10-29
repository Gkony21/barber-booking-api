package gr.barber.booking.push;

import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the phone shows: a title, a line of text, and the page that opens when it is tapped.
 * The service worker (push-sw.js in the web app) reads exactly these fields.
 */
public record PushMessage(String title, String body, String url) {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    public String json() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("title", title);
        fields.put("body", body);
        fields.put("url", url);
        return JSON.writeValueAsString(fields);
    }
}
