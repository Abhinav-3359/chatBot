const LeafMark = ({ className = "", accentColor = "var(--color-accent)" }) => (
  <svg viewBox="0 0 32 32" className={className} fill="none">
    <path d="M6 26C4 16 10 6 22 5c1 9-4 18-16 21z" fill="currentColor" />
    <path
      d="M9 23C10 15 15 9 23 8c0.6 7-3 14-14 15z"
      fill={accentColor}
      opacity="0.9"
    />
  </svg>
);

export default LeafMark;
