export default function StatCard({ icon: Icon, title, value, hint }) {
  return (
    <div className="card stat">
      <div className="icon">
        <Icon size={22} />
      </div>

      <div>
        <p>{title}</p>
        <h2>{value}</h2>
        <span>{hint}</span>
      </div>
    </div>
  );
}
