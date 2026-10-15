# TraceIt: Application Functionality & Route Tracking Guide

> [!NOTE]
> **Core Motivation**: **TraceIt** was created specifically to give parents and caregivers peace of mind when their child is transported by school bus. By automatically tracking the exact route taken during school commutes, parents can verify that the bus route is optimal, safe, and not unnecessarily long or winding.

---

## 1. Overview & Core Purpose

School bus routes can sometimes be circuitous, taking children on unnecessarily long detours before reaching school or home. **TraceIt** provides automated, accurate, and background-resilient GPS tracking designed around a child's school schedule.

Instead of manually starting and stopping tracking every day, parents set up schedules and geographical boundaries (geofences) once. The application runs autonomously in the background, records every movement into standard GPX files, calculates distance and travel time statistics, and displays the complete path on an interactive OpenStreetMap.

---

## 2. Key Functional Capabilities

### A. Smart Scheduling & Automated Tracking (`TrackingScheduler`)
- **Custom Time Ranges**: Define specific active hours and minutes (e.g., morning bus pickup at 07:30 to 08:30, afternoon return at 14:30 to 15:30).
- **Days of the Week Selection**: Restrict tracking to school days (Monday–Friday) or customize specific days.
- **Configurable GPS Intervals**: Adjust polling frequency (default 5 seconds) to balance accuracy and battery consumption.
- **Multiple Schedules**: Support for multiple distinct schedules (e.g., different schedules for different days or after-school activities).

### B. Spatial Geofencing: Start & End Point Radius (`LocationService`)
- **Departure & Arrival Geofences**:
    - **Start Point**: Designated home location with a configurable radius (e.g., 50 meters). Tracking initiates automatically once the device departs the home radius during scheduled hours.
    - **End Point**: Designated school location with a configurable radius. Tracking automatically marks the trip as completed once the device enters the school radius.
- **Visual Geofence Setup**: Parents can pick start and end points directly by tapping on the interactive map or entering coordinates, complete with visual green (start) and red (end) radius circles.

### C. Intelligent Background Tracking & Battery Optimization (`LocationService`)
- **Foreground Service with Wake Lock**: Runs reliably as an Android foreground service with a persistent notification showing active status and trip progress.
- **Activity Recognition & Motion Sensing**:
    - Integrates with Google Play Services **Activity Recognition** (detecting `STILL` vs moving states) and the hardware **Significant Motion Sensor**.
    - Automatically pauses tracking when the device is stationary for extended periods (e.g., when the bus is parked or stopped in traffic) and resumes upon detecting motion.
    - **Stillness Timeout**: Built-in 5-minute inactivity stillness checker to prevent battery drain.

### D. Interactive Map Visualization (`TrackMap` / OSMDroid)
- **OpenStreetMap Integration**: Uses `osmdroid` for offline-capable, smooth map rendering without proprietary map dependencies.
- **Visual Route Overlays**:
    - Blue polyline tracing the exact path taken.
    - Numbered markers and highlighted circles for start and end geofences.
    - Start (green) and latest/end (red) position markers.
- **Map Controls**: Quick zoom in/out, centering on current GPS location, and interactive map tapping when selecting schedule points.

### E. GPX Track Logging & Management (`GpxManager`)
- **Standard GPX Format**: Logs track points (latitude, longitude, elevation, timestamp) into structured XML GPX files named by date and schedule (`track_YYYYMMDD_HHMM_HHMM.gpx`).
- **Trip Statistics**: Automatically computes total distance (meters/kilometers) and duration (hours/minutes/seconds).
- **Point Inspector**: View a chronological or reversed list of all logged GPS points with elevation data and ignore status.
- **Saved Tracks Management**: Browse past recorded trips, load any historical track onto the map for comparison, delete unwanted files, or export GPX files using the system Storage Access Framework document picker.

### F. Privacy, & UI Customization
- **Privacy & Compliance**: User MessagingPlatform (UMP) consent dialog support and Open Source license viewer.
- **Localization**: Fully translated into multiple languages (English, German, Spanish, French, Polish, Portuguese).
- **Dynamic Theming**: Supports System, Light, and Dark theme modes.

---

## 3. How It Ensures Route Optimization for School Buses

1. **Passive Reliability**: Once configured with home/school coordinates and school hours, the app requires zero manual interaction. It silently records the school bus journey every school day.
2. **Objective Data**: By reviewing the generated GPX files and distance/duration statistics over time, parents can spot anomalies (e.g., unexpected detours, excessive travel time) and compare routes between different days.
3. **Data Portability**: Exported GPX files can be opened in advanced mapping tools or shared with school authorities to discuss route improvements if the bus ride is found to be too long or suboptimal.
