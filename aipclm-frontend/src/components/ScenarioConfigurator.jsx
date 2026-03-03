import { useState, useCallback } from 'react';

/* ─── Enum Options (mirror backend) ─── */

const WEATHER_OPTIONS  = ['CLEAR', 'CLOUDY', 'OVERCAST', 'RAIN', 'THUNDERSTORM', 'SNOW', 'ICE', 'FOG'];
const VISIBILITY_OPTIONS = ['UNLIMITED', 'GOOD', 'MODERATE', 'LOW', 'VERY_LOW', 'ZERO'];
const TIME_OPTIONS     = ['DAY', 'DUSK', 'NIGHT'];
const TERRAIN_OPTIONS  = ['FLAT', 'MOUNTAINOUS', 'COASTAL', 'DESERT', 'URBAN'];
const RUNWAY_OPTIONS   = ['DRY', 'WET', 'CONTAMINATED', 'ICY', 'FLOODED'];
const MISSION_OPTIONS  = ['ROUTINE', 'TRAINING', 'COMBAT', 'MEDICAL_EVAC', 'CARGO', 'VIP'];
const EMERGENCY_OPTIONS = ['NONE', 'ENGINE_FAILURE', 'HYDRAULIC_LOSS', 'BIRD_STRIKE',
  'CABIN_DEPRESSURIZATION', 'FUEL_LEAK', 'ELECTRICAL_FAILURE', 'FIRE', 'GEAR_MALFUNCTION'];
const DIFFICULTY_OPTIONS = ['NORMAL', 'MODERATE', 'EXTREME'];

const DEFAULT_SCENARIO = {
  difficultyPreset:  'NORMAL',
  weatherCondition:  'CLEAR',
  visibility:        'UNLIMITED',
  timeOfDay:         'DAY',
  terrainType:       'FLAT',
  runwayCondition:   'DRY',
  missionType:       'ROUTINE',
  emergencyType:     'NONE',
  windSpeedKnots:    5,
  windGustKnots:     0,
  crosswindComponent: 0,
  temperatureC:      15,
  moonIllumination:  0.0,
  runwayLengthFt:    9000,
  airportElevationFt: 30,
};

/* ─── Preset quick-fill values ─── */
const PRESETS = {
  NORMAL: { ...DEFAULT_SCENARIO },
  MODERATE: {
    ...DEFAULT_SCENARIO,
    difficultyPreset: 'MODERATE',
    weatherCondition: 'RAIN',
    visibility: 'MODERATE',
    timeOfDay: 'DUSK',
    terrainType: 'COASTAL',
    runwayCondition: 'WET',
    windSpeedKnots: 18,
    windGustKnots: 28,
    crosswindComponent: 12,
    temperatureC: 8,
  },
  EXTREME: {
    ...DEFAULT_SCENARIO,
    difficultyPreset: 'EXTREME',
    weatherCondition: 'THUNDERSTORM',
    visibility: 'VERY_LOW',
    timeOfDay: 'NIGHT',
    terrainType: 'MOUNTAINOUS',
    runwayCondition: 'ICY',
    missionType: 'COMBAT',
    emergencyType: 'ENGINE_FAILURE',
    windSpeedKnots: 45,
    windGustKnots: 65,
    crosswindComponent: 30,
    temperatureC: -12,
    moonIllumination: 0.1,
    runwayLengthFt: 6000,
    airportElevationFt: 5400,
  },
};

/* ─── Helpers ─── */
const label = (s) => s.replace(/_/g, ' ');

const severityColor = (preset) => {
  if (preset === 'EXTREME')  return '#FF3333';
  if (preset === 'MODERATE') return '#FFD700';
  return '#00FF41';
};

/* ─── Component ─── */
export default function ScenarioConfigurator({ value, onChange }) {
  const [expanded, setExpanded] = useState(false);
  const scenario = value || DEFAULT_SCENARIO;

  const set = useCallback((field, val) => {
    onChange({ ...scenario, [field]: val });
  }, [scenario, onChange]);

  const applyPreset = useCallback((preset) => {
    onChange({ ...PRESETS[preset] });
  }, [onChange]);

  return (
    <div className="scenario-cfg">
      {/* ── Header toggle ── */}
      <button
        className="scenario-cfg__toggle"
        onClick={() => setExpanded((e) => !e)}
        type="button"
      >
        <span className="scenario-cfg__toggle-label">
          <span className="scenario-cfg__icon">◆</span>
          SCENARIO CONFIG
        </span>
        <span className="scenario-cfg__toggle-badge" style={{ color: severityColor(scenario.difficultyPreset) }}>
          {scenario.difficultyPreset}
        </span>
        <span className="scenario-cfg__chevron" style={{ transform: expanded ? 'rotate(180deg)' : 'rotate(0deg)' }}>
          ▾
        </span>
      </button>

      {/* ── Expandable body ── */}
      {expanded && (
        <div className="scenario-cfg__body">

          {/* Quick Presets */}
          <div className="scenario-cfg__section">
            <div className="scenario-cfg__section-label">DIFFICULTY PRESET</div>
            <div className="scenario-cfg__btn-row">
              {DIFFICULTY_OPTIONS.map((d) => (
                <button
                  key={d}
                  onClick={() => applyPreset(d)}
                  className={`hud-btn ${scenario.difficultyPreset === d ? 'hud-btn--primary' : 'hud-btn--ghost'}`}
                  style={{ fontSize: '0.68rem', padding: '0.22rem 0.55rem', flex: 1 }}
                  type="button"
                >
                  {d}
                </button>
              ))}
            </div>
          </div>

          {/* Two-column grid */}
          <div className="scenario-cfg__grid">

            {/* Weather */}
            <OptionGroup
              label="WEATHER"
              options={WEATHER_OPTIONS}
              value={scenario.weatherCondition}
              onChange={(v) => set('weatherCondition', v)}
            />

            {/* Visibility */}
            <OptionGroup
              label="VISIBILITY"
              options={VISIBILITY_OPTIONS}
              value={scenario.visibility}
              onChange={(v) => set('visibility', v)}
            />

            {/* Time of Day */}
            <OptionGroup
              label="TIME OF DAY"
              options={TIME_OPTIONS}
              value={scenario.timeOfDay}
              onChange={(v) => set('timeOfDay', v)}
            />

            {/* Terrain */}
            <OptionGroup
              label="TERRAIN"
              options={TERRAIN_OPTIONS}
              value={scenario.terrainType}
              onChange={(v) => set('terrainType', v)}
            />

            {/* Runway */}
            <OptionGroup
              label="RUNWAY CONDITION"
              options={RUNWAY_OPTIONS}
              value={scenario.runwayCondition}
              onChange={(v) => set('runwayCondition', v)}
            />

            {/* Mission */}
            <OptionGroup
              label="MISSION TYPE"
              options={MISSION_OPTIONS}
              value={scenario.missionType}
              onChange={(v) => set('missionType', v)}
            />

            {/* Emergency */}
            <OptionGroup
              label="EMERGENCY"
              options={EMERGENCY_OPTIONS}
              value={scenario.emergencyType}
              onChange={(v) => set('emergencyType', v)}
              danger
            />
          </div>

          {/* Sliders row */}
          <div className="scenario-cfg__section">
            <div className="scenario-cfg__section-label">ENVIRONMENT PARAMETERS</div>
            <div className="scenario-cfg__sliders">
              <SliderField label="Wind (kt)"     min={0}  max={80}   value={scenario.windSpeedKnots}     onChange={(v) => set('windSpeedKnots', v)} />
              <SliderField label="Gusts (kt)"     min={0}  max={100}  value={scenario.windGustKnots}      onChange={(v) => set('windGustKnots', v)} />
              <SliderField label="Crosswind (kt)" min={0}  max={50}   value={scenario.crosswindComponent} onChange={(v) => set('crosswindComponent', v)} />
              <SliderField label="Temp (°C)"      min={-40} max={55}  value={scenario.temperatureC}       onChange={(v) => set('temperatureC', v)} />
              <SliderField label="Runway (ft)"    min={3000} max={14000} value={scenario.runwayLengthFt}   onChange={(v) => set('runwayLengthFt', v)} step={500} />
              <SliderField label="Elev (ft)"      min={0}  max={14000}  value={scenario.airportElevationFt} onChange={(v) => set('airportElevationFt', v)} step={100} />
              <SliderField label="Moon"           min={0}  max={1}    value={scenario.moonIllumination}   onChange={(v) => set('moonIllumination', v)} step={0.1} decimal />
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

/* ─── Sub-components ─── */

function OptionGroup({ label: groupLabel, options, value, onChange, danger }) {
  return (
    <div className="scenario-cfg__group">
      <div className="scenario-cfg__group-label">{groupLabel}</div>
      <div className="scenario-cfg__chips">
        {options.map((opt) => {
          const active = value === opt;
          const isDanger = danger && opt !== 'NONE' && active;
          return (
            <button
              key={opt}
              type="button"
              onClick={() => onChange(opt)}
              className={`scenario-chip ${active ? (isDanger ? 'scenario-chip--danger' : 'scenario-chip--active') : ''}`}
            >
              {label(opt)}
            </button>
          );
        })}
      </div>
    </div>
  );
}

function SliderField({ label: fieldLabel, min, max, value, onChange, step = 1, decimal }) {
  const display = decimal ? Number(value).toFixed(1) : value;
  return (
    <div className="scenario-slider">
      <div className="scenario-slider__header">
        <span className="scenario-slider__label">{fieldLabel}</span>
        <span className="scenario-slider__value">{display}</span>
      </div>
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={(e) => onChange(decimal ? parseFloat(e.target.value) : parseInt(e.target.value, 10))}
        className="scenario-slider__input"
      />
    </div>
  );
}

/* ─── Export default scenario for external use ─── */
export { DEFAULT_SCENARIO };
