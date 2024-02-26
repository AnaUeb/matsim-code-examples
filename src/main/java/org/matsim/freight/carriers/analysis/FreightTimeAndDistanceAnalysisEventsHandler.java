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
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.freight.carriers.Carrier;
import org.matsim.freight.carriers.Carriers;
import org.matsim.freight.carriers.CarriersUtils;
import org.matsim.freight.carriers.Tour;
import org.matsim.freight.carriers.events.CarrierTourEndEvent;
import org.matsim.freight.carriers.events.CarrierTourStartEvent;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author Kai Martins-Turner (kturner)
 */
public class FreightTimeAndDistanceAnalysisEventsHandler implements BasicEventHandler {

	private final static Logger log = LogManager.getLogger(FreightTimeAndDistanceAnalysisEventsHandler.class);

	private final Scenario scenario;
	Carriers carriers;
	private final Map<Id<Vehicle>, Double> vehicleId2TourDuration = new LinkedHashMap<>();
	private final Map<Id<Vehicle>, Double> vehicleId2TourLength = new LinkedHashMap<>();

	private final Map<Id<Vehicle>, Id<Carrier>> vehicleId2CarrierId = new LinkedHashMap<>();
	private final Map<Id<Vehicle>, Id<Tour>> vehicleId2TourId = new LinkedHashMap<>();

	private final Map<Id<VehicleType>, Double> vehicleTypeId2SumOfDuration = new LinkedHashMap<>();
	private final Map<Id<VehicleType>, Double> vehicleTypeId2Mileage = new LinkedHashMap<>();
	private final Map<Id<Vehicle>, VehicleType> vehicleId2VehicleType = new TreeMap<>();

	private final Map<String, Double> tourStartTime = new LinkedHashMap<>();


	public FreightTimeAndDistanceAnalysisEventsHandler(Scenario scenario, Carriers carriers) {
		this.scenario = scenario;
		this.carriers = carriers;
	}

	private void handleEvent(CarrierTourStartEvent event) {
		// Save time of freight tour start
		final String key = event.getCarrierId().toString() + "_" + event.getTourId().toString();
		tourStartTime.put(key, event.getTime());
	}

	//Fix costs for vehicle usage
	private void handleEvent(CarrierTourEndEvent event) {
		final String key = event.getCarrierId().toString() + "_" + event.getTourId().toString();
		double tourDuration = event.getTime() - tourStartTime.get(key);
		vehicleId2TourDuration.put(event.getVehicleId(), tourDuration);
		VehicleType vehType = VehicleUtils.findVehicle(event.getVehicleId(), scenario).getType();
		vehicleTypeId2SumOfDuration.merge(vehType.getId(), tourDuration, Double::sum);

		//Some general information for this vehicle
		vehicleId2CarrierId.putIfAbsent(event.getVehicleId(), event.getCarrierId());
		vehicleId2TourId.putIfAbsent(event.getVehicleId(), event.getTourId());

		vehicleId2VehicleType.putIfAbsent(event.getVehicleId(), vehType);
	}

	private void handleEvent(LinkEnterEvent event) {
		final double distance = scenario.getNetwork().getLinks().get(event.getLinkId()).getLength();
		vehicleId2TourLength.merge(event.getVehicleId(), distance, Double::sum);

		final Id<VehicleType> vehTypeId = VehicleUtils.findVehicle(event.getVehicleId(), scenario).getType().getId();
		vehicleTypeId2Mileage.merge(vehTypeId, distance, Double::sum);
	}

	@Override public void handleEvent(Event event) {

		if (event instanceof CarrierTourStartEvent carrierTourStartEvent) {
			handleEvent(carrierTourStartEvent);
		} else if (event instanceof CarrierTourEndEvent carrierTourEndEvent) {
			handleEvent(carrierTourEndEvent);
		} else if (event instanceof LinkEnterEvent linkEnterEvent) {
			handleEvent(linkEnterEvent);
		}
	}

	void writeTravelTimeAndDistance(String analysisOutputDirectory, Scenario scenario) throws IOException {
		log.info("Writing out Time & Distance & Costs ... perVehicle");
		//Travel time and distance per vehicle
		String fileName = analysisOutputDirectory + "TimeDistance_perVehicle.csv";

		BufferedWriter bw1 = new BufferedWriter(new FileWriter(fileName));

		//Write headline:
		//TODO: Fix dashboard-4-vehicleTypeTime and delete last column
		bw1.write("vehicleId ; carrierId ; vehicleTypeId ; tourId ; tourDuration[s] ; travelDistance[m] ; " +
				"costPerSecond[EUR/s] ; costPerMeter[EUR/m] ; fixedCosts[EUR] ; varCostsTime[EUR] ; varCostsDist[EUR] ; totalCosts[EUR]; summe");
		bw1.newLine();

		for (Id<Vehicle> vehicleId : vehicleId2VehicleType.keySet()) {

			final Double durationInSeconds = vehicleId2TourDuration.get(vehicleId);
			final Double distanceInMeters = vehicleId2TourLength.get(vehicleId);

			final VehicleType vehicleType = VehicleUtils.findVehicle(vehicleId, scenario).getType();
			final Double costsPerSecond = vehicleType.getCostInformation().getCostsPerSecond();
			final Double costsPerMeter = vehicleType.getCostInformation().getCostsPerMeter();
			final Double fixedCost = vehicleType.getCostInformation().getFixedCosts();

			final double varCostsTime = durationInSeconds * costsPerSecond;
			final double varCostsDist = distanceInMeters * costsPerMeter;
			final double totalVehCosts = fixedCost + varCostsTime + varCostsDist;

			bw1.write(vehicleId.toString());
			bw1.write(";" + vehicleId2CarrierId.get(vehicleId));
			bw1.write(";" + vehicleType.getId().toString());
			bw1.write(";" + vehicleId2TourId.get(vehicleId));

			bw1.write(";" + durationInSeconds);
			bw1.write(";" + distanceInMeters);
			bw1.write(";" + costsPerSecond);
			bw1.write(";" + costsPerMeter);
			bw1.write(";" + fixedCost);
			bw1.write(";" + varCostsTime);
			bw1.write(";" + varCostsDist);
			//TODO: Fix dashboard-4-vehicleTypeTime and delete the 1
			bw1.write(";" + totalVehCosts+";1");

			bw1.newLine();
		}

		bw1.close();
		log.info("Output written to " + fileName);
	}


	void writeTravelTimeAndDistancePerVehicleType(String analysisOutputDirectory, Scenario scenario) throws IOException {
		log.info("Writing out Time & Distance & Costs ... perVehicleType");

		//----- All VehicleTypes in CarriervehicleTypes container. Used so that even unused vehTypes appear in the output

		TreeMap<Id<VehicleType>, VehicleType> vehicleTypesMap = new TreeMap<>(CarriersUtils.getCarrierVehicleTypes(scenario).getVehicleTypes());
		//For the case that there are additional vehicle types found in the events.
		for (VehicleType vehicleType : vehicleId2VehicleType.values()) {
			vehicleTypesMap.putIfAbsent(vehicleType.getId(), vehicleType);
		}

		String fileName = analysisOutputDirectory + "TimeDistance_perVehicleType.csv";

		BufferedWriter bw1 = new BufferedWriter(new FileWriter(fileName));

		//Write headline:
		bw1.write("vehicleTypeId ; nuOfVehicles ; SumOfTourDuration[s] ; SumOfTravelDistances[m] ; " +
				"costPerSecond[EUR/s]; costPerMeter[EUR/m] ; fixedCosts[EUR/veh] ;" +
				"varCostsTime[EUR]; varCostsDist[EUR] ; fixedCosts[EUR] ; totalCosts[EUR]");
		bw1.newLine();


		for (VehicleType vehicleType : vehicleTypesMap.values()) {

			long nuOfVehicles = vehicleId2VehicleType.values().stream().filter(vehType -> vehType.getId() == vehicleType.getId()).count();

			final Double costRatePerSecond = vehicleType.getCostInformation().getCostsPerSecond();
			final Double costRatePerMeter = vehicleType.getCostInformation().getCostsPerMeter();
			final Double fixedCostPerVeh = vehicleType.getCostInformation().getFixedCosts();

			final Double sumOfDurationInSeconds = vehicleTypeId2SumOfDuration.getOrDefault(vehicleType.getId(), 0.);
			final Double sumOfDistanceInMeters = vehicleTypeId2Mileage.getOrDefault(vehicleType.getId(), 0.);

			final double sumOfVarCostsTime = sumOfDurationInSeconds * costRatePerSecond;
			final double sumOfVarCostsDistance = sumOfDistanceInMeters * costRatePerMeter;
			final double sumOfFixCosts = nuOfVehicles * fixedCostPerVeh;

			bw1.write(vehicleType.getId().toString());

			bw1.write(";" + nuOfVehicles);
			bw1.write(";" + sumOfDurationInSeconds);
			bw1.write(";" + sumOfDistanceInMeters);
			bw1.write(";" + costRatePerSecond);
			bw1.write(";" + costRatePerMeter);
			bw1.write(";" + fixedCostPerVeh);
			bw1.write(";" + sumOfVarCostsTime);
			bw1.write(";" + sumOfVarCostsDistance);
			bw1.write(";" + sumOfFixCosts);
			bw1.write(";" + (sumOfFixCosts + sumOfVarCostsTime + sumOfVarCostsDistance));

			bw1.newLine();
		}

		bw1.close();
		log.info("Output written to " + fileName);
	}

//added by AUE to create dashboard tiles
	void writeGeneralStats(String analysisOutputDirectory) throws IOException {
		log.info("Writing out general analysis ...");
		//Load per vehicle
		String fileName = analysisOutputDirectory + "General_stats.csv";

		BufferedWriter bw1 = new BufferedWriter(new FileWriter(fileName));

		//Define parameters
		double matsimscore = 0.0;
		double jspritscore = 0.0;
		int carrierNr = 0;
		int tours = 0;
		int shipments = 0;
		int services = 0;
		double durationInMin = 0;
		double distanceInKm = 0;

		//total distance and duration
		for (Id<Vehicle> vehicleId : vehicleId2VehicleType.keySet()) {

			durationInMin += vehicleId2TourDuration.get(vehicleId);
			distanceInKm += vehicleId2TourLength.get(vehicleId);

		}

		//other stats
		final TreeMap<Id<Carrier>, Carrier> sortedCarrierMap = new TreeMap<>(carriers.getCarriers());

		for (Carrier carrier : sortedCarrierMap.values()) {
			carrierNr += 1;
			matsimscore +=  carrier.getSelectedPlan().getScore();
			jspritscore +=  carrier.getSelectedPlan().getJspritScore();
			tours += carrier.getSelectedPlan().getScheduledTours().size();
			shipments += carrier.getShipments().size();
			services += carrier.getServices().size();
		}

		bw1.write("Number of carriers,"+carrierNr+",");
		bw1.newLine();
		bw1.write("Total tour duration,"+Math.round(100*durationInMin/60/60)/100+" h,");
		bw1.newLine();
		bw1.write("Total travel distance,"+ Math.round(100*distanceInKm/1000)/100+" km,");
		bw1.newLine();
		bw1.write("Number of tours,"+tours+",");
		bw1.newLine();
		bw1.write("Number of shipments,"+shipments+",");
		bw1.newLine();
		bw1.write("Number of services,"+services+",");
		bw1.newLine();
		bw1.write("Total MATSim Score,"+matsimscore+",");
		bw1.newLine();
		bw1.write("Total jsprit Score,"+Math.round(100*jspritscore)/100+",");
		bw1.newLine();


		bw1.close();
		log.info("Output written to " + fileName);
	}
}

