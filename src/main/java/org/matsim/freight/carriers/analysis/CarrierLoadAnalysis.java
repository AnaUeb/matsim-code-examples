/*
 *   *********************************************************************** *
 *   project: org.matsim.*
 *   *********************************************************************** *
 *                                                                           *
 *   copyright       : (C)  by the members listed in the COPYING,        *
 *                     LICENSE and WARRANTY file.                            *
 *   email           : info at matsim dot org                                *
 *                                                                           *
 *   *********************************************************************** *
 *                                                                           *
 *     This program is free software; you can redistribute it and/or modify  *
 *     it under the terms of the GNU General Public License as published by  *
 *     the Free Software Foundation; either version 2 of the License, or     *
 *     (at your option) any later version.                                   *
 *     See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                           *
 *   ***********************************************************************
 *
 */

package org.matsim.freight.carriers.analysis;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.Event;
import org.matsim.freight.carriers.Carriers;
import org.matsim.freight.carriers.CarriersUtils;
import org.matsim.freight.carriers.events.CarrierShipmentDeliveryStartEvent;
import org.matsim.freight.carriers.events.CarrierShipmentPickupStartEvent;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import static org.matsim.freight.carriers.events.CarrierEventAttributes.ATTRIBUTE_CAPACITYDEMAND;

/**
 * @author Kai Martins-Turner (kturner)
 */
public class CarrierLoadAnalysis implements BasicEventHandler {

	private static final Logger log = LogManager.getLogger(CarrierLoadAnalysis.class);

	Carriers carriers;

	private final Map<Id<Vehicle>, LinkedList<Integer>> vehicle2Load = new LinkedHashMap<>();

	public CarrierLoadAnalysis(Carriers carriers) {
		this.carriers = carriers;
	}

	private final Map<Id<Vehicle>, VehicleType> vehicleId2VehicleType = new TreeMap<>();

	@Override public void handleEvent(Event event) {
		if (event.getEventType().equals(CarrierShipmentPickupStartEvent.EVENT_TYPE)) {
			handlePickup( event);
		} if (event.getEventType().equals(CarrierShipmentDeliveryStartEvent.EVENT_TYPE)) {
			handleDelivery(event);
		}
	}

	private void handlePickup(Event event) {
		Id<Vehicle> vehicleId = Id.createVehicleId(event.getAttributes().get("vehicle"));
		Integer demand = Integer.valueOf(event.getAttributes().get(ATTRIBUTE_CAPACITYDEMAND));

		LinkedList<Integer> list;
		if (! vehicle2Load.containsKey(vehicleId)){
			list = new LinkedList<>();
			list.add(demand);
		} else {
			list = vehicle2Load.get(vehicleId);
			list.add(list.getLast() + demand);
		}
		vehicle2Load.put(vehicleId, list);
	}


	private void handleDelivery(Event event) {
		Id<Vehicle> vehicleId = Id.createVehicleId(event.getAttributes().get("vehicle"));
		Integer demand = Integer.valueOf(event.getAttributes().get(ATTRIBUTE_CAPACITYDEMAND));

		var list = vehicle2Load.get(vehicleId);
		list.add(list.getLast() - demand);
		vehicle2Load.put(vehicleId, list);
	}

	void writeLoadPerVehicle(String analysisOutputDirectory, Scenario scenario) throws IOException {
		log.info("Writing out vehicle load analysis ...");

		//Load per vehicle
		String fileName = analysisOutputDirectory + "Load_perVehicle.csv";

		BufferedWriter bw1 = new BufferedWriter(new FileWriter(fileName));

		//Write headline:
		bw1.write("vehicleId ; vehicleTypeId ; capacity ; maxLoad ; unusedCapacity; load state during tour");
		bw1.newLine();


		// for calculation
		double dif = 0;
		List<Double> perc = new ArrayList();
		List<String> types = new ArrayList();

		for (Id<Vehicle> vehicleId : vehicle2Load.keySet()) {

			final LinkedList<Integer> load = vehicle2Load.get(vehicleId);
			final Integer maxLoad = load.stream().max(Comparator.naturalOrder()).get();

			final VehicleType vehicleType = VehicleUtils.findVehicle(vehicleId, scenario).getType();
			final Double capacity = vehicleType.getCapacity().getOther();

			dif = capacity - maxLoad;
			perc.add(maxLoad/capacity);
			if (!types.contains(vehicleType.getId().toString())) {
				types.add(vehicleType.getId().toString());
			}

			bw1.write(vehicleId.toString());
			bw1.write(";"+vehicleType.getId().toString());
			bw1.write(";" + capacity);
			bw1.write(";" + maxLoad);
			bw1.write(";" + dif);
			bw1.write(";" + load);
			bw1.newLine();
		}

		bw1.close();
		log.info("Output written to " + fileName);

		//Load total
		String fileName1 = analysisOutputDirectory + "Load_summary.csv";
		BufferedWriter bw2 = new BufferedWriter(new FileWriter(fileName1));
		double use = Math.round(perc.stream().mapToDouble(Double::doubleValue).sum()/ perc.size()*100);

		//Write file
		bw2.write("Used vehicle types,"+ types.size() +",car");
		bw2.newLine();
		bw2.write("Average use of capacity,"+ use+"%,chart-pie");
		bw2.close();
		log.info("Output written to " + fileName1);

	}


}