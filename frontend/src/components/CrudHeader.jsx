import { Plus } from 'lucide-react';

export default function CrudHeader({ title, hint, onAdd }) {
  return (
    <div className="card-title">
      <div>
        <h2>{title}</h2>
        <span>{hint}</span>
      </div>

      <button className="small-primary" onClick={onAdd}>
        <Plus size={16} />
        Adaugă
      </button>
    </div>
  );
}
