# ======================================================
# NDTI TIME SERIES ANIMATION
# Creates monthly animated NDTI maps from GeoTIFFs
# ======================================================

library(terra)
library(ggplot2)
library(ggspatial)
library(magick)
library(stringr)
library(sf)


# ------------------------------------------------------
# 1. FILE PATHS
# ------------------------------------------------------
folder <- "D:/AlmanorImagery"

files <- list.files(
  folder,
  pattern = "NDTI_.*\\.tif$",
  full.names = TRUE
)

files <- sort(files)


# ------------------------------------------------------
# 2. LOAD STUDY AREA
# ------------------------------------------------------
lake <- st_read(
  "D:Almanor.shp",
  quiet = TRUE
)

lake <- st_transform(lake, "EPSG:32610")


# ------------------------------------------------------
# 3. FIRE EVENT DATES
# ------------------------------------------------------
fire_start <- as.Date("2021-07-13")
fire_end   <- as.Date("2021-10-25")


# ------------------------------------------------------
# 4. CREATE PLOTS
# ------------------------------------------------------
plots <- vector("list", length(files))

for (i in seq_along(files)) {

  # Load raster
  r <- rast(files[i])

  # Convert raster to dataframe for ggplot
  df <- as.data.frame(r, xy = TRUE, na.rm = TRUE)
  names(df)[3] <- "NDTI"

  # Extract date from filename
  name <- basename(files[i])

  date_str <- name |>
    str_remove("NDTI_") |>
    str_remove("\\.tif")

  frame_date <- as.Date(paste0(date_str, "-01"))

  # Create map
  p <- ggplot() +

    geom_raster(
      data = df,
      aes(x = x, y = y, fill = NDTI)
    ) +

    geom_sf(
      data = lake,
      fill = NA,
      color = "black",
      linewidth = 0.5
    ) +

    scale_fill_gradient(
      low = "#e5f5e0",
      high = "#006d2c",
      limits = c(-0.5, 0.5),
      oob = scales::squish,
      name = "NDTI"
    ) +

    coord_sf() +

    ggtitle(
      paste("Almanor Lake NDTI -", date_str)
    ) +

    theme_minimal() +

    theme(
      axis.text = element_blank(),
      axis.title = element_blank()
    ) +

    annotation_scale(
      location = "bl",
      width_hint = 0.3
    ) +

    annotation_north_arrow(
      location = "tr",
      which_north = "true",
      style = north_arrow_fancy_orienteering
    )

  # Add fire label during fire months
  if (frame_date >= fire_start &&
      frame_date <= fire_end) {

    p <- p +
      annotate(
        "label",
        x = min(df$x) + diff(range(df$x)) * 0.05,
        y = max(df$y) - diff(range(df$y)) * 0.05,
        label = "Carr Fire (2018)",
        hjust = 0,
        vjust = 1,
        size = 5,
        fill = "white",
        color = "red",
        fontface = "bold"
      )
  }

  plots[[i]] <- p
}


# ------------------------------------------------------
# 5. CREATE GIF ANIMATION
# ------------------------------------------------------
frames <- image_graph(
  width = 600,
  height = 600,
  res = 96
)

for (p in plots) {
  print(p)
}

dev.off()

animation <- image_animate(
  frames,
  fps = 1
)


# ------------------------------------------------------
# 6. EXPORT GIF
# ------------------------------------------------------
image_write(
  animation,
  "Almanor_Dixiefire_ndti.gif"
)

print("Animation saved!")
