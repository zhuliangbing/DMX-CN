package systems.dmx.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;



public class JSONTest {

    private Logger logger = Logger.getLogger(getClass().getName());

    // --- Serialization ---

    @Test
    public void put() throws JSONException {
        JSONObject o = new JSONObject();
        JSONObject ret = o.put("value", "hello");
        //
        assertSame(o, ret);
    }

    @Test
    public void string() throws JSONException {
        String str = "hi";
        //
        JSONObject o = new JSONObject();
        o.put("id", 123);
        o.put("value", str);
        //
        assertEquals("{\"id\":123,\"value\":\"hi\"}", o.toString());
    }

    @Test
    public void nullObject() throws JSONException {
        String str = null;  // declaration is required. Otherwise the compiler complains "reference to put is ambiguous"
        //
        JSONObject o = new JSONObject();
        o.put("id", 123);
        o.put("value", str);
        // if the value is null, then the key will be removed
        assertEquals("{\"id\":123}", o.toString());
    }

    @Test
    public void remove() throws JSONException {
        String str = null;
        //
        JSONObject o = new JSONObject();
        o.put("id", 123);
        o.put("value", "hello");
        //
        assertEquals("hello", o.getString("value"));
        //
        // if the value is null, then the key will be removed
        o.put("value", str);
        assertEquals("nothing", o.optString("value", "nothing"));
    }

    @Test
    public void stringsArray() throws JSONException {
        List<String> strings = Arrays.asList("abc", "[123]", "{abc: 123}");
        JSONArray a = new JSONArray(strings);
        assertTrue(a.get(0) instanceof String);
        assertTrue(a.get(1) instanceof String);
        assertTrue(a.get(2) instanceof String);
        assertEquals("abc",        a.getString(0));
        assertEquals("[123]",      a.getString(1));
        assertEquals("{abc: 123}", a.getString(2));
        assertEquals("[\"abc\",\"[123]\",\"{abc: 123}\"]", a.toString());
    }

    @Test
    public void classWithToStringMethod() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", 123);
        o.put("value", new ClassWithToStringMethod());
        // Every type of object can be but. For serialization the object's toString() method is consulted.
        assertEquals("{\"id\":123,\"value\":\"456\"}", o.toString());
    }

    @Test
    public void classWithNullReturningToStringMethod() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", 123);
        o.put("value", new ClassWithNullReturningToStringMethod());
        // If the object's toString() returns null it is serialized as "" (!)
        assertEquals("{\"id\":123,\"value\":\"\"}", o.toString());
    }

    @Test
    public void classWithoutToStringMethod() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", 123);
        o.put("value", new ClassWithoutToStringMethod());
        // if toString() is not overridden:
        assertTrue(o.toString().startsWith(
            "{\"id\":123,\"value\":\"systems.dmx.test.JSONTest$ClassWithoutToStringMethod@"));
    }

    // --- Get ---

    @Test
    public void getClassWithToStringMethod() throws JSONException {
        Object val = new ClassWithToStringMethod();
        //
        JSONObject o = new JSONObject();
        o.put("id", 123);
        o.put("value", val);
        assertEquals("{\"id\":123,\"value\":\"456\"}", o.toString());
        //
        Object obj = o.get("value");
        assertEquals("systems.dmx.test.JSONTest$ClassWithToStringMethod", obj.getClass().getName());
        assertSame(val, obj);   // the very object is stored in the JSONObject
        //
        String s = o.getString("value");
        assertEquals("456", s); // getting the object as string returns its toString() result
    }

    @Test
    public void getClassWithNullReturningToStringMethod() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", 123);
        o.put("value", new ClassWithNullReturningToStringMethod());
        assertEquals("{\"id\":123,\"value\":\"\"}", o.toString());
        //
        String s = o.getString("value");
        assertNull(s);  // getting the object as string returns its toString() result -- null in this case
    }

    // --- Default Values ---

    @Test
    public void defaultNumber() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", 123);
        assertEquals(456, o.optLong("value", 456));
        assertEquals(-1, o.optLong("value", -1));
        assertEquals(0, o.optLong("value"));
    }

    @Test
    public void defaultString() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", 123);
        assertEquals("test", o.optString("value", "test"));
        assertEquals("", o.optString("value"));
        assertNull(o.optString("value", null));
    }

    @Test
    public void defaultObject() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", 123);
        assertNull(o.optJSONObject("id"));      // the "id" value is not a JSONObject -> null is returned
        assertNull(o.optJSONObject("value"));
    }

    // --- NULL value ---

    @Test
    public void nullValue() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("value", JSONObject.NULL);
        assertEquals("null", o.getString("value"));     // JSONObject.NULL.toString() returns "null"
        assertEquals("null", o.optString("value"));
        assertEquals("null", o.optString("value", "nothing"));
    }

    @Test
    public void nullValueForInt() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("value", JSONObject.NULL);
        //
        // o.getInt("value") would throw JSONException: JSONObject["value"] is not a number
        assertEquals(0,   o.optInt("value"));
        assertEquals(123, o.optInt("value", 123));
    }

    @Test
    public void isNullValue() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("value", JSONObject.NULL);
        assertTrue(o.isNull("value"));      // isNull() returns true if the value associated with the key is null
        assertTrue(o.isNull("id"));         // ... or if there is no value
    }

    // --- Iteration ---

    @Test
    public void iteration() throws JSONException {
        List keys = new ArrayList();
        JSONObject o = new JSONObject("{d: 78, c: 90, a: 56, e: 12, b: 34}");
        Iterator<String> i = o.keys();
        while (i.hasNext()) {
            String key = i.next();
            keys.add(key);
        }
        assertEquals(Arrays.asList("d", "c", "a", "e", "b"), keys);
        // the jettison JSONObject implementation preserves the natural key order (uses LinkedHashMap)
    }

    @Test
    public void names() throws JSONException {
        JSONObject o = new JSONObject("{d: 78, c: 90, a: 56, e: 12, b: 34}");
        JSONArray keys = o.names();
        assertEquals("[\"d\",\"c\",\"a\",\"e\",\"b\"]", keys.toString());
        // the jettison JSONObject implementation preserves the natural key order (uses LinkedHashMap)
    }

    // --- Comments ---

    @Test
    public void comment() throws JSONException {
        JSONObject o = new JSONObject("{abc: 123, /* comment */ def: 456}");
        assertEquals(123, o.getInt("abc"));
        assertEquals(456, o.getInt("def"));
        // the jettison JSON parser allows comments!
        // That's surprising as comments are not covered by the JSON specification.
    }

    @Test
    public void commentToLineEnd() throws JSONException {
        JSONObject o = new JSONObject("{abc: 123, // comment\ndef: 456}");
        assertEquals(123, o.getInt("abc"));
        assertEquals(456, o.getInt("def"));
        // the jettison JSON parser allows comments!
        // That's surprising as comments are not covered by the JSON specification.
    }

    @Test
    public void commentHash() throws JSONException {
        JSONObject o = new JSONObject("{abc: 123, # comment\ndef: 456}");
        assertEquals(123, o.getInt("abc"));
        assertEquals(456, o.getInt("def"));
        // the jettison JSON parser allows comments!
        // That's surprising as comments are not covered by the JSON specification.
    }

    // --- Helper Classes ---

    class ClassWithToStringMethod {
        public String toString() {
            return "456";
        }
    }

    class ClassWithNullReturningToStringMethod {
        public String toString() {
            return null;
        }
    }

    class ClassWithoutToStringMethod {
    }
}
