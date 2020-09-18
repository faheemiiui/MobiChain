package com.ciit.ps.helper;

import com.ciit.lp.entities.WorkerNode;

public class WorkerDistance { // Witness/LA distance from Prover

	private WorkerNode worker;
	private Double distance;
	
	public WorkerDistance(WorkerNode worker, Double distance) {
		super();
		this.worker = worker;
		this.distance = distance;
	}

	public WorkerNode getWorker() {
		return worker;
	}

	public void setWorker(WorkerNode worker) {
		this.worker = worker;
	}

	public Double getDistance() {
		return distance;
	}

	public void setDistance(Double distance) {
		this.distance = distance;
	}
	
	public String toString()
	{
		return worker+"[Distance:"+distance+"]";
	}
}
