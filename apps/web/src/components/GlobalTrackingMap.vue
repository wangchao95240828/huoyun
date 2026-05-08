<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import maplibregl, { type StyleSpecification } from "maplibre-gl";
import { ArcLayer, ScatterplotLayer, TextLayer } from "@deck.gl/layers";
import { MapboxOverlay } from "@deck.gl/mapbox";
import "maplibre-gl/dist/maplibre-gl.css";

interface GeoPoint {
  name: string;
  countryCode: string;
  lat: number;
  lng: number;
}

interface TrackingRoute {
  id: string;
  shipmentNo: string;
  trackingNo: string;
  serviceMode: string;
  customerDirection: string;
  carrierName: string;
  channelName?: string;
  status: string;
  shipmentStatus: string;
  rawStatus: string;
  latestLocation: string;
  latestEventTime?: string;
  destinationCountry: string;
  destinationPostalCode?: string;
  progress: number;
  origin: GeoPoint;
  current: GeoPoint;
  destination: GeoPoint;
}

interface LayerRoute {
  id: string;
  route: TrackingRoute;
  selected: boolean;
  source: [number, number];
  target: [number, number];
  current: [number, number];
  color: [number, number, number];
}

const props = defineProps<{
  routes: TrackingRoute[];
  selectedRouteId?: string;
}>();

const emit = defineEmits<{
  select: [route: TrackingRoute];
}>();

let map: maplibregl.Map | null = null;
let overlay: MapboxOverlay | null = null;
let animationFrame = 0;
const mapElement = ref<HTMLDivElement | null>(null);

const darkMapStyle: StyleSpecification = {
  version: 8,
  sources: {
    "carto-dark": {
      type: "raster",
      tiles: [
        "https://a.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}@2x.png",
        "https://b.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}@2x.png",
        "https://c.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}@2x.png",
      ],
      tileSize: 256,
      attribution: "© OpenStreetMap © CARTO",
    },
  },
  layers: [
    {
      id: "background",
      type: "background",
      paint: { "background-color": "#07111f" },
    },
    {
      id: "carto-dark",
      type: "raster",
      source: "carto-dark",
      paint: {
        "raster-opacity": 0.7,
        "raster-saturation": -0.55,
        "raster-contrast": 0.2,
      },
    },
  ],
};

function normalizeLng(lng: number): number {
  if (!Number.isFinite(lng)) return 0;
  let value = lng;
  while (value > 180) value -= 360;
  while (value < -180) value += 360;
  return value;
}

function pointPosition(point: GeoPoint): [number, number] {
  return [normalizeLng(Number(point.lng)), Number(point.lat)];
}

function progressPosition(route: TrackingRoute): [number, number] {
  const source = pointPosition(route.origin);
  const target = pointPosition(route.destination);
  const progress = Math.max(0, Math.min(100, route.progress ?? 0)) / 100;
  let targetLng = target[0];
  if (Math.abs(targetLng - source[0]) > 180) {
    targetLng += targetLng < source[0] ? 360 : -360;
  }
  return [
    normalizeLng(source[0] + (targetLng - source[0]) * progress),
    source[1] + (target[1] - source[1]) * progress,
  ];
}

function routeColor(route: TrackingRoute, selected: boolean): [number, number, number] {
  if (selected) return [34, 211, 238];
  if (route.status === "EXCEPTION") return [239, 68, 68];
  if (route.status === "DELIVERED" || route.status === "CLOSED") return [34, 197, 94];
  if (route.status === "CREATED" || route.status === "ORDERED") return [245, 158, 11];
  return route.customerDirection === "SELLER_CUSTOMER" ? [59, 130, 246] : [20, 184, 166];
}

function routeData(): LayerRoute[] {
  return props.routes.map(route => {
    const selected = route.id === props.selectedRouteId;
    return {
      id: route.id,
      route,
      selected,
      source: pointPosition(route.origin),
      target: pointPosition(route.destination),
      current: progressPosition(route),
      color: routeColor(route, selected),
    };
  });
}

function updateDeckLayers(time = performance.now()): void {
  if (!overlay) return;
  const pulse = (Math.sin(time / 520) + 1) / 2;
  const data = routeData();
  const selectedData = data.filter(item => item.selected);
  overlay.setProps({
    pickingRadius: 10,
    onClick: info => {
      const object = info.object as LayerRoute | undefined;
      if (object?.route) emit("select", object.route);
    },
    getTooltip: info => {
      const object = info.object as LayerRoute | undefined;
      if (!object?.route) return null;
      const route = object.route;
      return {
        html: `<strong>${route.shipmentNo}</strong><br/>${route.carrierName} · ${route.latestLocation}<br/>${route.rawStatus} · ${route.progress}%`,
        style: {
          backgroundColor: "rgba(5, 15, 28, .94)",
          color: "#dffafe",
          border: "1px solid rgba(34, 211, 238, .35)",
          borderRadius: "8px",
          fontSize: "12px",
          lineHeight: "1.6",
        },
      };
    },
    layers: [
      new ArcLayer<LayerRoute>({
        id: "shipment-arcs",
        data,
        getSourcePosition: d => d.source,
        getTargetPosition: d => d.target,
        getSourceColor: d => [...d.color, d.selected ? 230 : 145],
        getTargetColor: d => d.route.status === "EXCEPTION" ? [239, 68, 68, 230] : [...d.color, d.selected ? 245 : 170],
        getWidth: d => d.selected ? 4.5 : 2,
        getHeight: d => d.selected ? 0.72 : 0.42,
        greatCircle: true,
        pickable: true,
      }),
      new ScatterplotLayer<LayerRoute>({
        id: "shipment-destinations",
        data,
        getPosition: d => d.target,
        getFillColor: d => d.route.status === "EXCEPTION" ? [239, 68, 68, 230] : [245, 158, 11, d.selected ? 245 : 185],
        getLineColor: [255, 255, 255, 210],
        getLineWidth: d => d.selected ? 2 : 1,
        getRadius: d => d.selected ? 74000 : 52000,
        radiusUnits: "meters",
        stroked: true,
        pickable: true,
      }),
      new ScatterplotLayer<LayerRoute>({
        id: "shipment-current",
        data,
        getPosition: d => d.current,
        getFillColor: d => [...d.color, d.selected ? 245 : 200],
        getLineColor: [255, 255, 255, 230],
        getLineWidth: d => d.selected ? 2.5 : 1.4,
        getRadius: d => (d.selected ? 93000 : 61000) + pulse * (d.selected ? 52000 : 24000),
        radiusUnits: "meters",
        stroked: true,
        pickable: true,
      }),
      new ScatterplotLayer<LayerRoute>({
        id: "shipment-origins",
        data,
        getPosition: d => d.source,
        getFillColor: [20, 184, 166, 220],
        getLineColor: [255, 255, 255, 180],
        getLineWidth: 1,
        getRadius: 42000,
        radiusUnits: "meters",
        stroked: true,
      }),
      new TextLayer<LayerRoute>({
        id: "selected-label",
        data: selectedData,
        getPosition: d => d.target,
        getText: d => `${d.route.destination.name} ${d.route.progress}%`,
        getSize: 13,
        getColor: [224, 242, 254, 245],
        getPixelOffset: [0, -22],
        background: true,
        getBackgroundColor: [7, 17, 31, 210],
        backgroundPadding: [8, 4],
      }),
    ],
  });
}

function fitRoutes(): void {
  if (!map || !props.routes.length) return;
  const bounds = new maplibregl.LngLatBounds();
  props.routes.forEach(route => {
    bounds.extend(pointPosition(route.origin));
    bounds.extend(pointPosition(route.destination));
    bounds.extend(progressPosition(route));
  });
  if (!bounds.isEmpty()) {
    map.fitBounds(bounds, {
      padding: { top: 52, right: 52, bottom: 52, left: 52 },
      maxZoom: 3.2,
      duration: 700,
    });
  }
}

function animate(time: number): void {
  updateDeckLayers(time);
  animationFrame = requestAnimationFrame(animate);
}

onMounted(() => {
  if (!mapElement.value) return;
  map = new maplibregl.Map({
    container: mapElement.value,
    style: darkMapStyle,
    center: [42, 24],
    zoom: 1.35,
    minZoom: 1,
    maxZoom: 8,
    pitch: 42,
    bearing: -10,
    attributionControl: false,
  });
  map.addControl(new maplibregl.NavigationControl({ visualizePitch: true }), "top-right");
  map.addControl(new maplibregl.AttributionControl({ compact: true }), "bottom-right");
  overlay = new MapboxOverlay({ interleaved: false });
  map.addControl(overlay as unknown as maplibregl.IControl);
  map.once("load", () => {
    updateDeckLayers();
    fitRoutes();
    animationFrame = requestAnimationFrame(animate);
  });
});

watch(
  () => [props.routes, props.selectedRouteId] as const,
  () => {
    updateDeckLayers();
    fitRoutes();
  },
  { deep: true }
);

onBeforeUnmount(() => {
  cancelAnimationFrame(animationFrame);
  overlay?.finalize();
  overlay = null;
  map?.remove();
  map = null;
});
</script>

<template>
  <div class="global-tracking-map">
    <div ref="mapElement" class="map-canvas" />
    <div class="map-scanline" />
    <div class="map-orbit orbit-one" />
    <div class="map-orbit orbit-two" />
  </div>
</template>

<style scoped>
.global-tracking-map {
  min-height: 324px;
  border: 1px solid #164e63;
  border-radius: var(--radius);
  background:
    radial-gradient(circle at 18% 22%, rgba(34, 211, 238, .18), transparent 32%),
    radial-gradient(circle at 72% 20%, rgba(59, 130, 246, .14), transparent 30%),
    linear-gradient(135deg, #06111f, #0b1728 58%, #07111f);
  position: relative;
  overflow: hidden;
  box-shadow:
    inset 0 0 0 1px rgba(34, 211, 238, .08),
    0 18px 44px rgba(2, 8, 23, .18);
}
.map-canvas {
  position: absolute;
  inset: 0;
}
.map-scanline {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    linear-gradient(rgba(34, 211, 238, .055) 1px, transparent 1px),
    linear-gradient(90deg, rgba(34, 211, 238, .04) 1px, transparent 1px);
  background-size: 36px 36px;
  mix-blend-mode: screen;
}
.map-orbit {
  position: absolute;
  pointer-events: none;
  border: 1px solid rgba(34, 211, 238, .14);
  border-radius: 50%;
  transform: rotate(-12deg);
}
.orbit-one {
  width: 58%;
  height: 32%;
  left: 20%;
  top: 31%;
}
.orbit-two {
  width: 84%;
  height: 46%;
  left: 8%;
  top: 24%;
  border-color: rgba(59, 130, 246, .10);
}
:deep(.maplibregl-ctrl-group) {
  border: 1px solid rgba(34, 211, 238, .2);
  background: rgba(6, 17, 31, .82);
  box-shadow: 0 10px 26px rgba(2, 8, 23, .24);
}
:deep(.maplibregl-ctrl button) {
  background-color: transparent;
}
:deep(.maplibregl-ctrl button span) {
  filter: invert(1) hue-rotate(150deg);
  opacity: .82;
}
:deep(.maplibregl-ctrl-attrib) {
  background: rgba(6, 17, 31, .78);
  color: #94a3b8;
}
:deep(.maplibregl-ctrl-attrib a) {
  color: #67e8f9;
}
</style>
