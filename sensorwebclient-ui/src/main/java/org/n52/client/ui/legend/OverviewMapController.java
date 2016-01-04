/**
 * Copyright (C) 2012-2016 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License version 2 as publishedby the Free
 * Software Foundation.
 *
 * If the program is linked with libraries which are licensed under one of the
 * following licenses, the combination of the program with the linked library is
 * not considered a "derivative work" of the program:
 *
 *     - Apache License, version 2.0
 *     - Apache Software License, version 1.0
 *     - GNU Lesser General Public License, version 3
 *     - Mozilla Public License, versions 1.0, 1.1 and 2.0
 *     - Common Development and Distribution License (CDDL), version 1.0
 *
 * Therefore the distribution of the program linked with libraries licensed under
 * the aforementioned licenses, is permitted by the copyright holders if the
 * distribution is compliant with both the GNU General Public License version 2
 * and the aforementioned licenses.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 */
package org.n52.client.ui.legend;

import java.util.ArrayList;

import org.gwtopenmaps.openlayers.client.MapWidget;
import org.gwtopenmaps.openlayers.client.Marker;
import org.n52.client.bus.EventBus;
import org.n52.client.sos.event.LegendElementSelectedEvent;
import org.n52.client.sos.event.ResizeEvent;
import org.n52.client.sos.event.TimeSeriesChangedEvent;
import org.n52.client.sos.event.data.DeleteTimeSeriesEvent;
import org.n52.client.sos.event.data.TimeSeriesHasDataEvent;
import org.n52.client.sos.event.data.handler.DeleteTimeSeriesEventHandler;
import org.n52.client.sos.event.data.handler.TimeSeriesHasDataEventHandler;
import org.n52.client.sos.event.handler.LegendElementSelectedEventHandler;
import org.n52.client.sos.event.handler.ResizeEventHandler;
import org.n52.client.sos.event.handler.TimeSeriesChangedEventHandler;
import org.n52.client.sos.legend.TimeseriesLegendData;
import org.n52.client.ui.map.InfoMarker;
import org.n52.client.ui.map.MapController;
import org.n52.client.ui.map.OpenlayersMarker;

public class OverviewMapController implements MapController {

	private final OverviewMap map;
	
	private String selectedTimeSeriesId;
	
	public OverviewMapController() {
		// TODO do we need reference to parent container?
		new OverviewMapControllerEventBroker(this);
		map = new OverviewMap(this);
	}
	

	public MapWidget createMap() {
		return map.getMapWidget();
	}
	
	public void updateMapContent() {
		map.updateMapContent();
	}
	
	public String getMapProjection() {
		return map.getMapProjection();
	}
	
	/**
	 * Convenience method to remove a popup (if there is one) from the map.
	 */
	public void removeShownPopup() {
		if (map.isShownPopup()) {
			map.removePopup();
		}
	}
	
//	private void setPopupOnMap(Popup popup) {
//		map.setPopup(popup);
//	}

	public void handleInfoMarkerClicked(InfoMarker infoMarker) {
//		removeShownPopup();
//		setPopupOnMap(infoMarker.getPopup());
	}
	
	private class OverviewMapControllerEventBroker implements
		    TimeSeriesChangedEventHandler,
		    ResizeEventHandler,
		    DeleteTimeSeriesEventHandler,
		    LegendElementSelectedEventHandler,
		    TimeSeriesHasDataEventHandler {
		
		private OverviewMapController controller;
		
		public OverviewMapControllerEventBroker(OverviewMapController controller) {
			this.controller = controller;
			EventBus eventBus = EventBus.getMainEventBus();
		    eventBus.addHandler(TimeSeriesChangedEvent.TYPE, this);
		    eventBus.addHandler(ResizeEvent.TYPE, this);
		    eventBus.addHandler(DeleteTimeSeriesEvent.TYPE, this);
		    eventBus.addHandler(LegendElementSelectedEvent.TYPE, this);
		    eventBus.addHandler(TimeSeriesHasDataEvent.TYPE, this);
		}
		
		public void onTimeSeriesChanged(TimeSeriesChangedEvent evt) {
			//TODO: only update when new Timeseries is loaded
		    controller.updateMapContent();
		}
		
		public void onResize(ResizeEvent evt) {
			controller.updateMapContent();
		}
		
		public void onDeleteTimeSeries(DeleteTimeSeriesEvent evt) {
		//    controller.updateMapContent();
		}
		
		public void onSelected(LegendElementSelectedEvent evt) {
		
		    if (evt.getElement().getDataWrapper() instanceof TimeseriesLegendData) {
		
		        TimeseriesLegendData ts = (TimeseriesLegendData) evt.getElement().getDataWrapper();
		        controller.setSelectedTimeSeries(ts.getId());
		        ArrayList<Marker> markers = controller.getMarkers();
		
		        for (Marker m : markers) {
		            if (m instanceof OpenlayersMarker) {
		                OpenlayersMarker olm = (OpenlayersMarker) m;
		                if (olm.containsTS(ts.getId())) {
		                    olm.mark();
		                }
		                else {
		                    olm.unmark();
		                }
		            }
		        }
		    }
		}
		
		public void onHasData(TimeSeriesHasDataEvent evt) {
			controller.updateMapContent();
		}
	}
	
	public String getSelectedTimeSeriesId() {
    	return selectedTimeSeriesId;
    }
	
    private void setSelectedTimeSeries(String timeSeriesId) {
    	this.selectedTimeSeriesId = timeSeriesId;
    }
    
    public boolean isSelectedTimeSeries() {
    	return selectedTimeSeriesId != null && selectedTimeSeriesId.length() > 0;
    }
    
    protected ArrayList<Marker> getMarkers() {
        return map.getMarkers();
    }

	
}
