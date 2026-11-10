// FE-02: 환경 바(SC01-01~03). 초기값은 "선택 안 함"(WA-01), 운영 환경을 고른 쪽에 "운영 DB" 배지(NFR-07).
import type { Environment } from '../types';

interface Props {
  environments: Environment[];
  envA: string | null;
  envB: string | null;
  onChangeA: (env: string | null) => void;
  onChangeB: (env: string | null) => void;
  error?: string | null;
}

function EnvSelect(props: {
  id: string;
  label: string;
  value: string | null;
  environments: Environment[];
  onChange: (env: string | null) => void;
}) {
  const production = props.environments.find((e) => e.id === props.value)?.production ?? false;
  return (
    <div className={production ? 'env is-prod' : 'env'}>
      <label htmlFor={props.id}>{props.label}</label>
      <select
        id={props.id}
        value={props.value ?? ''}
        onChange={(e) => props.onChange(e.target.value === '' ? null : e.target.value)}
      >
        <option value="">선택 안 함</option>
        {props.environments.map((env) => (
          <option key={env.id} value={env.id}>
            {env.name}
          </option>
        ))}
      </select>
      {production && (
        <span className="badge-prod" data-testid={`${props.id}-prod-badge`}>
          ⚠ 운영 DB
        </span>
      )}
    </div>
  );
}

export function EnvBar(props: Props) {
  return (
    <header className="envbar">
      <span className="brand">DataMatch</span>
      <EnvSelect
        id="env-a"
        label="A쪽 환경"
        value={props.envA}
        environments={props.environments}
        onChange={props.onChangeA}
      />
      <EnvSelect
        id="env-b"
        label="B쪽 환경"
        value={props.envB}
        environments={props.environments}
        onChange={props.onChangeB}
      />
      {props.error && (
        <span className="env-error" role="alert">
          {props.error}
        </span>
      )}
    </header>
  );
}
