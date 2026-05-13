/********************************************************
 * NDTI WATER QUALITY ANALYSIS
 * Landsat 8 Collection 2 Level 2 Surface Reflectance
 ********************************************************/


/*------------------------------------------------------
  1. STUDY AREA
------------------------------------------------------*/
var almanor = ee.FeatureCollection(Almanor);
var lakeGeom = almanor.geometry();


/*------------------------------------------------------
  2. ANALYSIS SETTINGS
------------------------------------------------------*/
var startDate = ee.Date('2021-01-01');
var months = ee.List.sequence(0, 24); // 16 months total


/*------------------------------------------------------
  3. LOAD LANDSAT 8 COLLECTION
------------------------------------------------------*/
var landsat = ee.ImageCollection('LANDSAT/LC08/C02/T1_L2')
  .filterBounds(lakeGeom);


/*------------------------------------------------------
  4. PREPROCESSING FUNCTIONS
------------------------------------------------------*/

// Cloud mask using QA_PIXEL band
function maskL8(image) {
  var qa = image.select('QA_PIXEL');

  // Bit 3 = cloud
  var cloudMask = qa.bitwiseAnd(1 << 3).eq(0);

  return image
    .updateMask(cloudMask)
    .copyProperties(image, ['system:time_start']);
}


// Apply Landsat 8 SR scaling factors
function scaleL8(image) {
  var optical = image.select('SR_B.*')
    .multiply(0.0000275)
    .add(-0.2);

  return image.addBands(optical, null, true);
}


// Add NDTI band
// NDTI = (Red - Green) / (Red + Green)
function addNDTI(image) {
  var ndti = image.normalizedDifference(['SR_B4', 'SR_B3'])
    .rename('NDTI');

  return image.addBands(ndti);
}


// Add NDWI band for water masking
function addNDWI(image) {
  var ndwi = image.normalizedDifference(['SR_B3', 'SR_B5'])
    .rename('NDWI');

  return image.addBands(ndwi);
}


/*------------------------------------------------------
  5. BUILD PROCESSED IMAGE COLLECTION
------------------------------------------------------*/
var processed = landsat
  .map(maskL8)
  .map(scaleL8)
  .map(addNDTI)
  .map(addNDWI);


/*------------------------------------------------------
  6. CREATE MONTHLY COMPOSITES
------------------------------------------------------*/
function createMonthlyComposite(monthOffset) {

  var start = startDate.advance(monthOffset, 'month');
  var end = start.advance(1, 'month');

  // Monthly median composite
  var image = processed
    .filterDate(start, end)
    .median()
    .clip(lakeGeom);

  // Water mask
  var waterMask = image.select('NDWI').gt(0);

  // Mask NDTI to water only
  var ndtiMasked = image.select('NDTI')
    .updateMask(waterMask);

  return ndtiMasked
    .set('system:time_start', start.millis())
    .set('date', start.format('YYYY-MM'));
}


// Monthly NDTI image collection
var ndtiImages = ee.ImageCollection.fromImages(
  months.map(createMonthlyComposite)
);


/*------------------------------------------------------
  7. BUILD MONTHLY TIME SERIES
------------------------------------------------------*/
var ndtiSeries = ee.FeatureCollection(
  ndtiImages.map(function(image) {

    var mean = image.reduceRegion({
      reducer: ee.Reducer.mean(),
      geometry: lakeGeom,
      scale: 30,
      maxPixels: 1e13
    });

    return ee.Feature(null, {
      date: image.get('date'),
      NDTI: mean.get('NDTI')
    });
  })
);


/*------------------------------------------------------
  8. MAP DISPLAY
------------------------------------------------------*/
Map.centerObject(almanor, 10);

Map.addLayer(
  almanor,
  {color: 'blue'},
  'Lake Almanor'
);


/*------------------------------------------------------
  9. TIME SERIES CHART
------------------------------------------------------*/
var chart = ui.Chart.feature.byFeature(
    ndtiSeries,
    'date',
    'NDTI'
  )
  .setOptions({
    title: 'Lake Almanor NDTI Time Series',
    hAxis: {title: 'Date'},
    vAxis: {title: 'Mean NDTI'},
    lineWidth: 2,
    pointSize: 4
  });

print(chart);


/*------------------------------------------------------
  10. EXPORT TIME SERIES CSV
------------------------------------------------------*/
Export.table.toDrive({
  collection: ndtiSeries,
  description: 'Almanor_NDTI_TimeSeries',
  fileFormat: 'CSV'
});


/*------------------------------------------------------
  11. EXPORT MONTHLY NDTI IMAGES
------------------------------------------------------*/
var imageList = ndtiImages.toList(ndtiImages.size());
var imageCount = ndtiImages.size().getInfo();

for (var i = 0; i < imageCount; i++) {

  var image = ee.Image(imageList.get(i));
  var date = image.get('date').getInfo();

  Export.image.toDrive({
    image: image,
    description: 'NDTI_' + date,
    fileNamePrefix: 'NDTI_' + date,
    region: lakeGeom,
    scale: 30,
    maxPixels: 1e13
  });
}
