package com.ciit.ps.wn;

import akka.actor.AbstractActor;
import akka.actor.Props;

public class Worker extends AbstractActor {

	public static Props props() {
		return Props.create(Worker.class,() -> new Worker());
	}
	
	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(String.class, reply->{
					System.out.println(reply);
				})		
				.build();
	}

}
