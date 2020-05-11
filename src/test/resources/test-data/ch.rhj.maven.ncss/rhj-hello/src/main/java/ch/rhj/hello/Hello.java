package ch.rhj.hello;

public class Hello implements Runnable {

	@Override
	public void run() {

		System.out.println("hello, world!");
	}
}
