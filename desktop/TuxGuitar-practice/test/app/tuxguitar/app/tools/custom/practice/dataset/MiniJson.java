package app.tuxguitar.app.tools.custom.practice.dataset;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A minimal JSON reader for the annotation files of the datasets (objects, arrays, strings, numbers, booleans, null). */
final class MiniJson {

	private final String text;
	private int at;

	private MiniJson(String text) {
		this.text = text;
	}

	/** @return Map, List, String, Double, Boolean or null */
	static Object parse(String text) {
		MiniJson reader = new MiniJson(text);
		Object value = reader.value();
		reader.skip();
		if( reader.at != text.length() ) {
			throw new IllegalArgumentException("trailing characters at " + reader.at);
		}
		return value;
	}

	@SuppressWarnings("unchecked")
	static Map<String, Object> object(Object value) {
		return (Map<String, Object>) value;
	}

	@SuppressWarnings("unchecked")
	static List<Object> array(Object value) {
		return (List<Object>) value;
	}

	private void skip() {
		while( this.at < this.text.length() && Character.isWhitespace(this.text.charAt(this.at)) ) {
			this.at ++;
		}
	}

	private Object value() {
		skip();
		char c = this.text.charAt(this.at);
		switch( c ) {
			case '{':
				return object();
			case '[':
				return array();
			case '"':
				return string();
			case 't':
				expect("true");
				return Boolean.TRUE;
			case 'f':
				expect("false");
				return Boolean.FALSE;
			case 'n':
				expect("null");
				return null;
			default:
				return number();
		}
	}

	private void expect(String word) {
		if( !this.text.startsWith(word, this.at) ) {
			throw new IllegalArgumentException("expected " + word + " at " + this.at);
		}
		this.at += word.length();
	}

	private Map<String, Object> object() {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		this.at ++;
		skip();
		if( this.text.charAt(this.at) == '}' ) {
			this.at ++;
			return map;
		}
		while( true ) {
			skip();
			String key = string();
			skip();
			if( this.text.charAt(this.at ++) != ':' ) {
				throw new IllegalArgumentException("expected : at " + this.at);
			}
			map.put(key, value());
			skip();
			char c = this.text.charAt(this.at ++);
			if( c == '}' ) {
				return map;
			}
			if( c != ',' ) {
				throw new IllegalArgumentException("expected , or } at " + this.at);
			}
		}
	}

	private List<Object> array() {
		List<Object> list = new ArrayList<Object>();
		this.at ++;
		skip();
		if( this.text.charAt(this.at) == ']' ) {
			this.at ++;
			return list;
		}
		while( true ) {
			list.add(value());
			skip();
			char c = this.text.charAt(this.at ++);
			if( c == ']' ) {
				return list;
			}
			if( c != ',' ) {
				throw new IllegalArgumentException("expected , or ] at " + this.at);
			}
		}
	}

	private String string() {
		if( this.text.charAt(this.at) != '"' ) {
			throw new IllegalArgumentException("expected a string at " + this.at);
		}
		this.at ++;
		StringBuilder out = new StringBuilder();
		while( true ) {
			char c = this.text.charAt(this.at ++);
			if( c == '"' ) {
				return out.toString();
			}
			if( c == '\\' ) {
				char e = this.text.charAt(this.at ++);
				switch( e ) {
					case 'n':
						out.append('\n');
						break;
					case 't':
						out.append('\t');
						break;
					case 'r':
						out.append('\r');
						break;
					case 'b':
						out.append('\b');
						break;
					case 'f':
						out.append('\f');
						break;
					case 'u':
						out.append((char) Integer.parseInt(this.text.substring(this.at, this.at + 4), 16));
						this.at += 4;
						break;
					default:
						out.append(e);
				}
			} else {
				out.append(c);
			}
		}
	}

	private Double number() {
		int start = this.at;
		while( this.at < this.text.length() && "+-0123456789.eE".indexOf(this.text.charAt(this.at)) >= 0 ) {
			this.at ++;
		}
		if( start == this.at ) {
			throw new IllegalArgumentException("unexpected character at " + start);
		}
		return Double.valueOf(this.text.substring(start, this.at));
	}
}
