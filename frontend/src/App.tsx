import { useCallback, useEffect, useMemo, useState } from 'react'
import { HexColorPicker } from 'react-colorful'
import {
  defineCaptureArea,
  fetchOverrides,
  persistOverrides,
  startLighting,
  stopLighting,
  type LightingOverridesPayload,
  type RawColor,
} from './api/lighting'
import './App.css'

const PREVIEW_COLS = 22
const RESOURCE_TYPES = [
  'MANA',
  'RAGE',
  'ENERGY',
  'FOCUS',
  'FURY',
  'INSANITY',
  'MAELSTROM',
  'RUNIC_POWER',
] as const

const formatHex = (value: number): string => `#${Math.max(0, Math.min(0xffffff, Math.round(value))).toString(16).padStart(6, '0').toUpperCase()}`

const parseColorString = (value: string, fallback: string): string => {
  const trimmed = value.trim()
  if (/^#[0-9a-fA-F]{6}$/.test(trimmed)) {
    return trimmed.toUpperCase()
  }
  if (/^[0-9a-fA-F]{6}$/.test(trimmed)) {
    return `#${trimmed.toUpperCase()}`
  }
  if (/^0x[0-9a-fA-F]{6}$/i.test(trimmed)) {
    return `#${trimmed.substring(2).toUpperCase()}`
  }
  if (/^[0-9]+$/.test(trimmed)) {
    const numeric = Number.parseInt(trimmed, 10)
    if (!Number.isNaN(numeric)) {
      return formatHex(numeric)
    }
  }
  return fallback
}

const toHexColor = (value: RawColor, fallback: string): string => {
  if (typeof value === 'number') {
    return formatHex(value)
  }
  if (typeof value === 'string') {
    return parseColorString(value, fallback)
  }
  return fallback
}

const toNumericColor = (value: string): number => Number.parseInt(value.replace('#', ''), 16)

type OverridesState = {
  hpRow: number
  hpFirstCol: number
  hpLastCol: number
  resourceRow: number
  resourceFirstCol: number
  resourceLastCol: number
  hpColor: string
  resourceColor: string
  backgroundColor: string
  resourceColors: Record<string, string>
}

const DEFAULT_OVERRIDES: OverridesState = {
  hpRow: 0,
  hpFirstCol: 0,
  hpLastCol: PREVIEW_COLS - 1,
  resourceRow: 1,
  resourceFirstCol: 0,
  resourceLastCol: PREVIEW_COLS - 1,
  hpColor: '#00FF00',
  resourceColor: '#FFA500',
  backgroundColor: '#102040',
  resourceColors: {
    MANA: '#0000FF',
    RAGE: '#FF0000',
    ENERGY: '#FFA500',
    FOCUS: '#FFFF00',
    FURY: '#FF00FF',
    INSANITY: '#00FFFF',
    MAELSTROM: '#808080',
    RUNIC_POWER: '#00FFFF',
  },
}

const normaliseOverrides = (payload: LightingOverridesPayload): OverridesState => {
  const base = { ...DEFAULT_OVERRIDES }
  const resourceColors: Record<string, string> = { ...base.resourceColors }
  const rawResources = payload.resourceColors ?? {}

  RESOURCE_TYPES.forEach((type) => {
    resourceColors[type] = toHexColor(rawResources[type], resourceColors[type])
  })

  return {
    hpRow: payload.hpRow ?? base.hpRow,
    hpFirstCol: payload.hpFirstCol ?? base.hpFirstCol,
    hpLastCol: payload.hpLastCol ?? base.hpLastCol,
    resourceRow: payload.resourceRow ?? base.resourceRow,
    resourceFirstCol: payload.resourceFirstCol ?? base.resourceFirstCol,
    resourceLastCol: payload.resourceLastCol ?? base.resourceLastCol,
    hpColor: toHexColor(payload.hpColor, base.hpColor),
    resourceColor: toHexColor(payload.resourceColor, base.resourceColor),
    backgroundColor: toHexColor(payload.backgroundColor, base.backgroundColor),
    resourceColors,
  }
}

const serialiseOverrides = (state: OverridesState): LightingOverridesPayload => ({
  hpRow: state.hpRow,
  hpFirstCol: state.hpFirstCol,
  hpLastCol: state.hpLastCol,
  resourceRow: state.resourceRow,
  resourceFirstCol: state.resourceFirstCol,
  resourceLastCol: state.resourceLastCol,
  hpColor: toNumericColor(state.hpColor),
  resourceColor: toNumericColor(state.resourceColor),
  backgroundColor: toNumericColor(state.backgroundColor),
  resourceColors: Object.fromEntries(
    Object.entries(state.resourceColors).map(([key, color]) => [key, toNumericColor(color)]),
  ),
})

interface ColorPickerFieldProps {
  label: string
  value: string
  onChange: (value: string) => void
  disabled?: boolean
  className?: string
}

const ColorPickerField = ({ label, value, onChange, disabled, className }: ColorPickerFieldProps) => {
  const [inputValue, setInputValue] = useState(value)

  useEffect(() => {
    setInputValue(value)
  }, [value])

  const handleBlur = () => {
    const normalised = parseColorString(inputValue, value)
    if (normalised !== value) {
      onChange(normalised)
    } else {
      setInputValue(value)
    }
  }

  const handleColorChange = (next: string) => {
    if (!disabled) {
      onChange(next)
    }
  }

  return (
    <div className={`color-field${disabled ? ' disabled' : ''}${className ? ` ${className}` : ''}`}>
      <span className="field-label">{label}</span>
      <HexColorPicker color={value} onChange={handleColorChange} className="color-picker" />
      <div className="color-field-footer">
        <span className="color-swatch" style={{ backgroundColor: value }} aria-hidden />
        <input
          className="color-input"
          value={inputValue}
          onChange={(event) => setInputValue(event.target.value)}
          onBlur={handleBlur}
          aria-label={`Couleur ${label}`}
          disabled={disabled}
        />
      </div>
    </div>
  )
}

function App() {
  const [overrides, setOverrides] = useState<OverridesState>(DEFAULT_OVERRIDES)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [status, setStatus] = useState('Chargement...')
  const [isRunning, setIsRunning] = useState(false)
  const [hpPreview, setHpPreview] = useState(75)
  const [resourcePreview, setResourcePreview] = useState(40)

  useEffect(() => {
    const loadOverrides = async () => {
      try {
        setLoading(true)
        const payload = await fetchOverrides()
        setOverrides(normaliseOverrides(payload))
        setStatus('Overrides chargés')
      } catch (error) {
        console.error('Failed to load overrides', error)
        setStatus("Erreur lors du chargement des overrides")
      } finally {
        setLoading(false)
      }
    }

    void loadOverrides()
  }, [])

  const saveOverrides = useCallback(
    async (next: OverridesState) => {
      try {
        setSaving(true)
        const message = await persistOverrides(serialiseOverrides(next))
        setStatus(message ?? 'Couleurs mises à jour')
      } catch (error) {
        console.error('Failed to persist overrides', error)
        setStatus("Erreur lors de l'enregistrement des couleurs")
      } finally {
        setSaving(false)
      }
    },
    [],
  )

  const updateColor = useCallback(
    (key: keyof OverridesState, value: string) => {
      setOverrides((previous) => {
        const next: OverridesState = {
          ...previous,
          [key]: value,
        } as OverridesState
        void saveOverrides(next)
        return next
      })
    },
    [saveOverrides],
  )

  const updateResourceColor = useCallback(
    (resource: string, value: string) => {
      setOverrides((previous) => {
        const next: OverridesState = {
          ...previous,
          resourceColors: {
            ...previous.resourceColors,
            [resource]: value,
          },
        }
        void saveOverrides(next)
        return next
      })
    },
    [saveOverrides],
  )

  const applyWowPreset = () => {
    setOverrides((previous) => {
      const next: OverridesState = {
        ...previous,
        hpColor: '#00FF00',
        resourceColor: '#FFA500',
        backgroundColor: '#102040',
        resourceColors: {
          ...previous.resourceColors,
          MANA: '#0000FF',
          RAGE: '#FF0000',
          ENERGY: '#FFA500',
          FOCUS: '#FFFF00',
          FURY: '#FF00FF',
          INSANITY: '#00FFFF',
          MAELSTROM: '#808080',
          RUNIC_POWER: '#00FFFF',
        },
      }
      void saveOverrides(next)
      return next
    })
  }

  const resetDefaults = () => {
    setOverrides((previous) => {
      const next: OverridesState = {
        ...normaliseOverrides({}),
        hpRow: previous.hpRow,
        hpFirstCol: previous.hpFirstCol,
        hpLastCol: previous.hpLastCol,
        resourceRow: previous.resourceRow,
        resourceFirstCol: previous.resourceFirstCol,
        resourceLastCol: previous.resourceLastCol,
      }
      void saveOverrides(next)
      return next
    })
    setStatus('Couleurs réinitialisées')
  }

  const handleStart = async () => {
    try {
      setStatus('Démarrage en cours...')
      const message = await startLighting()
      setStatus(message ?? 'OCR en cours')
      setIsRunning(true)
    } catch (error) {
      console.error('Failed to start lighting', error)
      setStatus("Impossible de démarrer l'éclairage")
    }
  }

  const handleStop = async () => {
    try {
      setStatus('Arrêt en cours...')
      const message = await stopLighting()
      setStatus(message ?? 'Arrêté')
      setIsRunning(false)
    } catch (error) {
      console.error('Failed to stop lighting', error)
      setStatus("Impossible d'arrêter l'éclairage")
    }
  }

  const handleDefineArea = async () => {
    try {
      setStatus('Définition de la zone...')
      const message = await defineCaptureArea()
      setStatus(message ?? 'Zone OCR définie')
    } catch (error) {
      console.error('Failed to define capture area', error)
      setStatus('Échec lors de la définition de la zone OCR')
    }
  }

  const hpFill = useMemo(() => Math.round((hpPreview / 100) * PREVIEW_COLS), [hpPreview])
  const resourceFill = useMemo(() => Math.round((resourcePreview / 100) * PREVIEW_COLS), [resourcePreview])

  const renderKeys = (fill: number, activeColor: string) =>
    Array.from({ length: PREVIEW_COLS }).map((_, index) => (
      <span
        key={index}
        className="key"
        style={{ backgroundColor: index < fill ? activeColor : overrides.backgroundColor }}
      />
    ))

  return (
    <div className="app-shell">
      <header className="app-header">
        <h1 className="title">Phoenix Lighting Designer</h1>
        <div className="actions">
          <button className="button secondary" onClick={handleDefineArea} disabled={loading}>
            Définir la zone
          </button>
          <button className="button primary" onClick={handleStart} disabled={loading || isRunning}>
            Start
          </button>
          <button className="button" onClick={handleStop} disabled={loading || !isRunning}>
            Stop
          </button>
        </div>
      </header>

      <main className="app-content">
        <section className="panel">
          <div className="panel-title">Couleurs principales</div>
          <div className="panel-body color-grid">
            <ColorPickerField
              label="HP"
              value={overrides.hpColor}
              onChange={(value) => updateColor('hpColor', value)}
              disabled={loading || saving}
            />
            <ColorPickerField
              label="Fond du clavier & périphériques"
              value={overrides.backgroundColor}
              onChange={(value) => updateColor('backgroundColor', value)}
              disabled={loading || saving}
            />
            <ColorPickerField
              label="Ressource générique"
              value={overrides.resourceColor}
              onChange={(value) => updateColor('resourceColor', value)}
              disabled={loading || saving}
            />
            <div className="panel-side-actions">
              <button className="button chip" onClick={applyWowPreset} disabled={loading || saving}>
                Preset WoW
              </button>
              <button className="button chip" onClick={resetDefaults} disabled={loading || saving}>
                Réinitialiser
              </button>
            </div>
          </div>
        </section>

        <section className="panel">
          <div className="panel-title">Avancé : Ressources par type (WoW)</div>
          <div className="panel-body advanced-grid">
            {RESOURCE_TYPES.map((resource) => (
              <ColorPickerField
                key={resource}
                label={resource.replace('_', ' ')}
                value={overrides.resourceColors[resource]}
                onChange={(value) => updateResourceColor(resource, value)}
                disabled={loading || saving}
                className="advanced-cell"
              />
            ))}
          </div>
        </section>

        <section className="panel">
          <div className="panel-title">Aperçu (simulation)</div>
          <div className="panel-body preview-panel">
            <div className="slider-row">
              <label htmlFor="hp-slider">HP %</label>
              <input
                id="hp-slider"
                type="range"
                min={0}
                max={100}
                value={hpPreview}
                onChange={(event) => setHpPreview(Number(event.target.value))}
              />
              <span className="slider-value">{hpPreview}%</span>
            </div>
            <div className="slider-row">
              <label htmlFor="res-slider">Ressource %</label>
              <input
                id="res-slider"
                type="range"
                min={0}
                max={100}
                value={resourcePreview}
                onChange={(event) => setResourcePreview(Number(event.target.value))}
              />
              <span className="slider-value">{resourcePreview}%</span>
            </div>
            <div className="preview-rows">
              <div className="preview-row">{renderKeys(hpFill, overrides.hpColor)}</div>
              <div className="preview-row">{renderKeys(resourceFill, overrides.resourceColor)}</div>
            </div>
          </div>
        </section>
      </main>

      <footer className="statusbar">
        <span className="status-label">État :</span>
        <span className="status-value">{status}</span>
        {saving && <span className="status-saving">Enregistrement...</span>}
      </footer>
    </div>
  )
}

export default App
