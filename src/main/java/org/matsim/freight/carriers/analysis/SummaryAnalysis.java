// can be deleted
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
import org.matsim.freight.carriers.Carrier;
import org.matsim.freight.carriers.Carriers;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.TreeMap;

/**
 * Some basic analysis / data collection for {@link Carriers}(files)
 * <p></p>
 * For all carriers it writes out the:
 * - score of the selected plan
 * - number of tours (= vehicles) of the selected plan
 * - number of Services (input)
 * - number of shipments (input)
 * to a tsv-file.
 * @author Kai Martins-Turner (kturner)
 */
public class SummaryAnalysis {

	private static final Logger log = LogManager.getLogger(SummaryAnalysis.class);

	Carriers carriers;

	public SummaryAnalysis(Carriers carriers) {
		this.carriers = carriers;
	}

	public void runAnalysisAndWriteStats(String analysisOutputDirectory) throws IOException {
		log.info("Writing out summary analysis ...");
		//Load per vehicle
		String fileName = analysisOutputDirectory + "summary.csv";

		BufferedWriter bw1 = new BufferedWriter(new FileWriter(fileName));

		//Define parameters
		double matsimscore = 0.0;
		double jspritscore = 0.0;
		int carrierNr = 0;
		int tours = 0;
		int shipments = 0;
		int services = 0;

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
		bw1.write("Number of tours,"+tours+",");
		bw1.newLine();
		bw1.write("Number of shipments,"+shipments+",");
		bw1.newLine();
		bw1.write("Number of services,"+services+",");
		bw1.newLine();
		bw1.write("Total MATSim Score,"+matsimscore+",");
		bw1.newLine();
		bw1.write("Total jsprit Score,"+jspritscore+",");
		bw1.newLine();


		bw1.close();
		log.info("Output written to " + fileName);
	}
}