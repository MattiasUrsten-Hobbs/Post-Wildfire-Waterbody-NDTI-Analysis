# Post-Wildfire-Waterbody-NDTI-Analysis
This project includes Java code used in Google Earth Engine and R script used in R Studio. Aim of project was to determine NDTI values in waterbodies after wildfires.
NOTE that the GEE and R studio code only say lake almanor as it was the first lake done. Code for other lakes is exaclty the same except for file paths, shapefile names, asset names in GEE, and exported file names.

The workflow:
1. Define study area using lake shapefiles.
2. Define analysis time window.
3. Load landsat 8 Imagery.
4. Load NDTI and NDWI indices.
5. Create monthly composites of NDTI in the lake and clip to NDWI mask.
6. Create graph displaying values.
