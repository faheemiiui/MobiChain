package com.ciit.ps.helper;

import com.ciit.lp.entities.LocationAuthorityDetails;
import com.grum.geocalc.Coordinate;
import com.grum.geocalc.DegreeCoordinate;
import com.grum.geocalc.EarthCalc;
import com.grum.geocalc.Point;

public class LAData {

	private LocationAuthorityDetails locationAuthority;
	
	public LAData(LocationAuthorityDetails locationAuthority) {
		super();
		this.locationAuthority = locationAuthority;
	}
	
	public Point getLocation() {
		Coordinate lat = null;
		Coordinate lng = null;
		Point location = null;

		lat = new DegreeCoordinate(locationAuthority.getLatitude());
		lng = new DegreeCoordinate(locationAuthority.getLongitude());
		location = new Point(lat, lng);
		return location;
	}
	
	public double distanceFrom(Point fromlocation)
	{
		Coordinate lat = null;
		Coordinate lng = null;
		Point laLocation = null;

		lat = new DegreeCoordinate(locationAuthority.getLatitude());
		lng = new DegreeCoordinate(locationAuthority.getLongitude());
		laLocation = new Point(lat, lng);
		
		return EarthCalc.getDistance(laLocation, fromlocation);
	}
	
	public LocationAuthorityDetails getLocationAuthority() {
		return locationAuthority;
	}
	public void setLocationAuthority(LocationAuthorityDetails locationAuthority) {
		this.locationAuthority = locationAuthority;
	}
	
	
}
