interface TagListProps {
  tags: string[];
  activeTag?: string;
  onSelect?: (tag: string) => void;
  compact?: boolean;
}

export default function TagList({ tags, activeTag, onSelect, compact = false }: TagListProps) {
  if (tags.length === 0) {
    return null;
  }

  return (
    <div className={compact ? "tag-list tag-list-compact" : "tag-list"}>
      {tags.map((tag) => {
        const isActive = tag === activeTag;
        if (!onSelect) {
          return (
            <span className="tag" key={tag}>
              {tag}
            </span>
          );
        }

        return (
          <button
            className={isActive ? "tag tag-active" : "tag"}
            key={tag}
            onClick={() => onSelect(isActive ? "" : tag)}
            type="button"
          >
            {tag}
          </button>
        );
      })}
    </div>
  );
}

