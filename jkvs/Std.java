package jkvs;
public class Std {
	public void println(Object s) {
		System.out.println(s);
	}

	public void printf(String fmt, Object... s) {
		System.out.printf(fmt, s);
	}

	public void eprintln(Object s) {
		System.err.println(s);
	}

	public void eprintf(String fmt, Object... s) {
		System.err.printf(fmt, s);
	}
}
