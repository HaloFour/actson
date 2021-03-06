// MIT License
//
// Copyright (c) 2016 Michel Kraemer
//
// Permission is hereby granted, free of charge, to any person obtaining
// a copy of this software and associated documentation files (the
// "Software"), to deal in the Software without restriction, including
// without limitation the rights to use, copy, modify, merge, publish,
// distribute, sublicense, and/or sell copies of the Software, and to
// permit persons to whom the Software is furnished to do so, subject to
// the following conditions:
//
// The above copyright notice and this permission notice shall be
// included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
// EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
// NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
// LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
// OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
// WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package de.undercouch.actson;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests {@link JsonParser}
 * @author Michel Kraemer
 */
public class JsonParserTest {
  /**
   * Parse a JSON string with the {@link JsonParser} and return
   * a new JSON string generated by {@link PrettyPrinter}. Assert
   * that the input JSON string is valid.
   * @param json the JSON string to parse
   * @return the new JSON string
   */
  private String parse(String json) {
    JsonParser parser = new JsonParser();
    return parse(json, parser);
  }

  /**
   * Parse a JSON string with the given {@link JsonParser} and return
   * a new JSON string generated by {@link PrettyPrinter}. Assert
   * that the input JSON string is valid.
   * @param json the JSON string to parse
   * @param parser the parser to use
   * @return the new JSON string
   */
  private String parse(String json, JsonParser parser) {
    byte[] buf = json.getBytes(StandardCharsets.UTF_8);

    PrettyPrinter printer = new PrettyPrinter();

    int i = 0;
    int event;
    do {
      while ((event = parser.nextEvent()) == JsonEvent.NEED_MORE_INPUT) {
        i += parser.getFeeder().feed(buf, i, buf.length - i);
        if (i == buf.length) {
          parser.getFeeder().done();
        }
      }
      assertFalse("Invalid JSON text", event == JsonEvent.ERROR);
      printer.onEvent(event, parser);
    } while (event != JsonEvent.EOF);

    return printer.getResult();
  }

  /**
   * Parse a JSON string with the given {@link JsonParser} but expect that
   * parsing will fail.
   * @param json the JSON string to parse
   * @param parser the parser to use
   */
  private void parseFail(byte[] json, JsonParser parser) {
    boolean ok = true;
    int j = 0;
    int event;
    do {
      while ((event = parser.nextEvent()) == JsonEvent.NEED_MORE_INPUT) {
        while (!parser.getFeeder().isFull() && j < json.length) {
          parser.getFeeder().feed(json[j]);
          ++j;
        }
        if (j == json.length) {
          parser.getFeeder().done();
        }
      }
      ok &= (event != JsonEvent.ERROR);
    } while (ok && event != JsonEvent.EOF);
    assertFalse(ok);
  }

  /**
   * Assert that two JSON objects are equal
   * @param expected the expected JSON object
   * @param actual the actual JSON object
   */
  private static void assertJsonObjectEquals(String expected, String actual) {
    ObjectMapper mapper = new ObjectMapper();
    TypeReference<Map<String, Object>> ref =
        new TypeReference<Map<String, Object>>() { };
    try {
      Map<String, Object> em = mapper.readValue(expected, ref);
      Map<String, Object> am = mapper.readValue(actual, ref);
      assertEquals(em, am);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Assert that two JSON arrays are equal
   * @param expected the expected JSON array
   * @param actual the actual JSON array
   */
  private static void assertJsonArrayEquals(String expected, String actual) {
    ObjectMapper mapper = new ObjectMapper();
    TypeReference<List<Object>> ref = new TypeReference<List<Object>>() { };
    try {
      List<Object> el = mapper.readValue(expected, ref);
      List<Object> al = mapper.readValue(actual, ref);
      assertEquals(el, al);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Test if valid files can be parsed correctly
   * @throws IOException if one of the test files could not be read
   */
  @Test
  public void testPass() throws IOException {
    for (int i = 1; i <= 3; ++i) {
      URL u = getClass().getResource("pass" + i + ".txt");
      String json = IOUtils.toString(u, "UTF-8");
      if (json.startsWith("{")) {
        assertJsonObjectEquals(json, parse(json));
      } else {
        assertJsonArrayEquals(json, parse(json));
      }
    }
  }

  /**
   * Test if invalid files cannot be parsed
   * @throws IOException if one of the test files could not be read
   */
  @Test
  public void testFail() throws IOException {
    for (int i = 2; i <= 34; ++i) {
      URL u = getClass().getResource("fail" + i + ".txt");
      byte[] json = IOUtils.toByteArray(u);
      JsonParser parser = new JsonParser();

      // test for too many nested modes
      parser.setMaxDepth(16);
      assertEquals(16, parser.getMaxDepth());

      parseFail(json, parser);
    }
  }

  /**
   * Test if an empty object is parsed correctly
   */
  @Test
  public void emptyObject() {
    String json = "{}";
    assertJsonObjectEquals(json, parse(json));
  }

  /**
   * Test if an object with one property is parsed correctly
   */
  @Test
  public void simpleObject() {
    String json = "{\"name\": \"Elvis\"}";
    assertJsonObjectEquals(json, parse(json));
  }

  /**
   * Test if an empty array is parsed correctly
   */
  @Test
  public void emptyArray() {
    String json = "[]";
    assertJsonArrayEquals(json, parse(json));
  }

  /**
   * Test if a simple array is parsed correctly
   */
  @Test
  public void simpleArray() {
    String json = "[\"Elvis\", \"Max\"]";
    assertJsonArrayEquals(json, parse(json));
  }

  /**
   * Test if an array with mixed values is parsed correctly
   */
  @Test
  public void mixedArray() {
    String json = "[\"Elvis\", 132, \"Max\", 80.67]";
    assertJsonArrayEquals(json, parse(json));
  }

  /**
   * Test if a JSON text containing utf8 characters can be parsed
   */
  @Test
  public void utf8() {
    String json = "{\"name\": \"Bj\u0153rn\"}";
    assertJsonObjectEquals(json, parse(json));
  }

  /**
   * Test if a JSON text containing utf8 characters cannot be parsed
   * if the parser uses ASCII encoding
   */
  @Test
  public void utf8Fail() {
    byte[] json = "{\"name\": \"Bj\u0153rn\"}".getBytes(StandardCharsets.UTF_8);
    parseFail(json, new JsonParser(StandardCharsets.US_ASCII));
  }

  /**
   * Test if a JSON text containing invalid characters cannot be parsed
   * if the parser uses ASCII encoding
   */
  @Test
  public void malformedInput() {
    byte[] json = "{\"name\": \"\uffff\"}".getBytes(StandardCharsets.UTF_8);
    parseFail(json, new JsonParser(StandardCharsets.US_ASCII));
  }

  /**
   * Test if a JSON text containing invalid characters cannot be parsed
   * if the parser uses ASCII encoding
   */
  @Test
  public void controlCharacter() {
    byte[] json = "{\"name\": \"\u0001\"}".getBytes(StandardCharsets.UTF_8);
    parseFail(json, new JsonParser(StandardCharsets.US_ASCII));
  }

  /**
   * Test if the parsed character count is calculated correctly
   */
  @Test
  public void parsedCharacterCount() {
    byte[] json = "{\"name\": \"Bj\u0153rn\"}".getBytes(StandardCharsets.UTF_8);
    String jsonStr = new String(json, StandardCharsets.UTF_8);

    JsonParser parser = new JsonParser();
    parser.getFeeder().feed(json);
    parser.getFeeder().done();

    assertEquals(0, parser.getParsedCharacterCount());

    assertEquals(JsonEvent.START_OBJECT, parser.nextEvent());
    assertEquals(1, parser.getParsedCharacterCount());
    assertEquals('{', jsonStr.charAt(parser.getParsedCharacterCount() - 1));

    assertEquals(JsonEvent.FIELD_NAME, parser.nextEvent());
    assertEquals(7, parser.getParsedCharacterCount());
    assertEquals("\"name\"", jsonStr.substring(
        parser.getParsedCharacterCount() - 6,
        parser.getParsedCharacterCount()));

    assertEquals(JsonEvent.VALUE_STRING, parser.nextEvent());
    assertEquals(16, parser.getParsedCharacterCount());
    assertEquals("\"Bj\u0153rn\"", jsonStr.substring(
        parser.getParsedCharacterCount() - 7,
        parser.getParsedCharacterCount()));

    assertEquals(JsonEvent.END_OBJECT, parser.nextEvent());
    assertEquals(17, parser.getParsedCharacterCount());
    assertEquals('}', jsonStr.charAt(parser.getParsedCharacterCount() - 1));

    assertEquals(JsonEvent.EOF, parser.nextEvent());
    assertEquals(17, parser.getParsedCharacterCount());
    assertEquals('}', jsonStr.charAt(parser.getParsedCharacterCount() - 1));
  }

  /**
   * Test what happens if {@link JsonParser#nextEvent} is called too many times
   */
  @Test
  public void tooManyNextEvent() {
    String json = "{}";
    JsonParser parser = new JsonParser();
    assertJsonObjectEquals(json, parse(json, parser));
    assertEquals(JsonEvent.ERROR, parser.nextEvent());
  }

  /**
   * Make sure a number right before the end of the object can be parsed
   */
  @Test
  public void numberAndEndOfObject() {
    String json = "{\"n\":2}";
    JsonParser parser = new JsonParser();
    assertJsonObjectEquals(json, parse(json, parser));
  }

  /**
   * Make sure a fraction can be parsed
   */
  @Test
  public void fraction() {
    String json = "{\"n\":2.1}";
    JsonParser parser = new JsonParser();
    assertJsonObjectEquals(json, parse(json, parser));
  }

  /**
   * Test that the parser does not accept illegal numbers ending with a dot
   */
  @Test
  public void illegalNumber() {
    byte[] json = "{\"n\":-2.}".getBytes(StandardCharsets.UTF_8);
    parseFail(json, new JsonParser());
  }

  /**
   * Make sure '0e1' can be parsed
   */
  @Test
  public void zeroWithExp() {
    String json = "{\"n\":0e1}";
    JsonParser parser = new JsonParser();
    assertJsonObjectEquals(json, parse(json, parser));
  }

  /**
   * Test if a top-level empty string can be parsed
   */
  @Test
  public void topLevelEmptyString() {
    String json = "\"\"";
    JsonParser parser = new JsonParser();
    assertEquals("\"\"", parse(json, parser));
  }

  /**
   * Test if a top-level 'false' can be parsed
   */
  @Test
  public void topLevelFalse() {
    String json = "false";
    JsonParser parser = new JsonParser();
    assertEquals("false", parse(json, parser));
  }

  /**
   * Test if a top-level integer can be parsed
   */
  @Test
  public void topLevelInt() {
    String json = "42";
    JsonParser parser = new JsonParser();
    assertEquals("42", parse(json, parser));
  }

  /**
   * Make sure pre-mature end of file is detected correctly
   */
  @Test
  public void numberAndEof() {
    byte[] json = "{\"i\":42".getBytes(StandardCharsets.UTF_8);
    parseFail(json, new JsonParser());
  }

  /**
   * Test if a top-level zero can be parsed
   */
  @Test
  public void topLevelZero() {
    String json = "0";
    JsonParser parser = new JsonParser();
    assertEquals("0", parse(json, parser));
  }
}
