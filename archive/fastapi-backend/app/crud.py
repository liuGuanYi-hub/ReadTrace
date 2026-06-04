from datetime import datetime, timezone
import json
from typing import Any

from .database import get_connection
from .schemas import BookCreate, BookUpdate, BookStatus


def _now_iso() -> str:
    return datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")


def _tags_to_json(tags: list[str] | None) -> str:
    return json.dumps(tags or [], ensure_ascii=False)


def _json_to_tags(value: str | None) -> list[str]:
    if not value:
        return []
    try:
        tags = json.loads(value)
    except json.JSONDecodeError:
        return []
    if not isinstance(tags, list):
        return []
    return [tag for tag in tags if isinstance(tag, str)]


def _row_to_book(row: Any) -> dict[str, Any]:
    return {
        "id": row["id"],
        "title": row["title"],
        "author": row["author"],
        "cover_url": row["cover_url"],
        "category": row["category"],
        "status": row["status"],
        "rating": row["rating"],
        "tags": _json_to_tags(row["tags"]),
        "short_comment": row["short_comment"],
        "review": row["review"],
        "start_date": row["start_date"],
        "finish_date": row["finish_date"],
        "created_at": row["created_at"],
        "updated_at": row["updated_at"],
        "is_deleted": bool(row["is_deleted"]),
        "deleted_at": row["deleted_at"],
    }


def list_books(
    status: BookStatus | None = None,
    keyword: str | None = None,
    tag: str | None = None,
) -> list[dict[str, Any]]:
    where = ["is_deleted = 0"]
    params: list[Any] = []

    if status:
        where.append("status = ?")
        params.append(status)

    normalized_keyword = keyword.strip() if keyword else ""
    if normalized_keyword:
        where.append("(title LIKE ? OR author LIKE ?)")
        like = f"%{normalized_keyword}%"
        params.extend([like, like])

    sql = f"""
        SELECT *
        FROM books
        WHERE {" AND ".join(where)}
        ORDER BY updated_at DESC, id DESC
    """

    with get_connection() as connection:
        rows = connection.execute(sql, params).fetchall()

    books = [_row_to_book(row) for row in rows]
    normalized_tag = tag.strip() if tag else ""
    if normalized_tag:
        books = [book for book in books if normalized_tag in book["tags"]]
    return books


def get_book(book_id: int, include_deleted: bool = False) -> dict[str, Any] | None:
    where = ["id = ?"]
    params: list[Any] = [book_id]
    if not include_deleted:
        where.append("is_deleted = 0")

    with get_connection() as connection:
        row = connection.execute(
            f"SELECT * FROM books WHERE {' AND '.join(where)}",
            params,
        ).fetchone()

    return _row_to_book(row) if row else None


def create_book(payload: BookCreate) -> dict[str, Any]:
    data = payload.model_dump()
    now = _now_iso()

    columns = [
        "title",
        "author",
        "cover_url",
        "category",
        "status",
        "rating",
        "tags",
        "short_comment",
        "review",
        "start_date",
        "finish_date",
        "created_at",
        "updated_at",
    ]
    values = [
        data["title"],
        data.get("author"),
        data.get("cover_url"),
        data.get("category"),
        data.get("status") or "wishlist",
        data.get("rating"),
        _tags_to_json(data.get("tags")),
        data.get("short_comment"),
        data.get("review"),
        data.get("start_date"),
        data.get("finish_date"),
        now,
        now,
    ]

    placeholders = ", ".join("?" for _ in columns)
    with get_connection() as connection:
        cursor = connection.execute(
            f"INSERT INTO books ({', '.join(columns)}) VALUES ({placeholders})",
            values,
        )
        book_id = int(cursor.lastrowid)

    created = get_book(book_id)
    if created is None:
        raise RuntimeError("created book could not be loaded")
    return created


def update_book(book_id: int, payload: BookUpdate) -> dict[str, Any] | None:
    existing = get_book(book_id)
    if existing is None:
        return None

    data = payload.model_dump(exclude_unset=True)
    if not data:
        return existing

    if "tags" in data:
        data["tags"] = _tags_to_json(data["tags"])
    data["updated_at"] = _now_iso()

    assignments = ", ".join(f"{column} = ?" for column in data)
    values = list(data.values())
    values.append(book_id)

    with get_connection() as connection:
        connection.execute(
            f"UPDATE books SET {assignments} WHERE id = ? AND is_deleted = 0",
            values,
        )

    return get_book(book_id)


def archive_book(book_id: int) -> dict[str, Any] | None:
    now = _now_iso()
    with get_connection() as connection:
        cursor = connection.execute(
            """
            UPDATE books
            SET is_deleted = 1,
                deleted_at = ?,
                updated_at = ?
            WHERE id = ? AND is_deleted = 0
            """,
            [now, now, book_id],
        )
        if cursor.rowcount == 0:
            return None

    return get_book(book_id, include_deleted=True)
