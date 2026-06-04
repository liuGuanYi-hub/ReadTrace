import { BOOK_STATUSES, BOOK_STATUS_LABELS, type BookStatus } from "../types/book";

interface StatusFilterProps {
  value?: BookStatus;
  onChange: (status?: BookStatus) => void;
}

export default function StatusFilter({ value, onChange }: StatusFilterProps) {
  return (
    <nav className="status-filter" aria-label="阅读状态筛选">
      <button className={!value ? "status-item status-item-active" : "status-item"} onClick={() => onChange()} type="button">
        全部
      </button>
      {BOOK_STATUSES.map((status) => (
        <button
          className={value === status ? "status-item status-item-active" : "status-item"}
          key={status}
          onClick={() => onChange(status)}
          type="button"
        >
          {BOOK_STATUS_LABELS[status]}
        </button>
      ))}
    </nav>
  );
}

