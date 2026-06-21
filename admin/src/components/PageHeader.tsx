interface Props {
  title: string;
  subtitle?: string;
  action?: React.ReactNode;
}

export default function PageHeader({ title, subtitle, action }: Props) {
  return (
    <div style={{
      display: 'flex',
      alignItems: 'flex-start',
      justifyContent: 'space-between',
      marginBottom: 28,
      gap: 16,
    }}>
      <div>
        <h1 className="page-title">{title}</h1>
        {subtitle && (
          <p style={{
            marginTop: 4,
            fontSize: 'var(--fs-body-sm)',
            color: 'var(--text-soft)',
          }}>{subtitle}</p>
        )}
      </div>
      {action && <div style={{ flexShrink: 0, paddingTop: 4 }}>{action}</div>}
    </div>
  );
}
