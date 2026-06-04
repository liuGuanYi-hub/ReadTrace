from datetime import date
from decimal import Decimal
import re
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator

BookStatus = Literal["wishlist", "reading", "finished", "paused", "dropped"]

DATE_PATTERN = re.compile(r"^\d{4}-\d{2}-\d{2}$")


def _empty_to_none(value: object) -> object:
    if isinstance(value, str):
        stripped = value.strip()
        return stripped or None
    return value


def _validate_rating(value: float | None) -> float | None:
    if value is None:
        return value
    decimal_value = Decimal(str(value))
    if decimal_value.as_tuple().exponent < -1:
        raise ValueError("rating must have at most one decimal place")
    return value


def _validate_tags(value: list[str] | None) -> list[str] | None:
    if value is None:
        return value
    cleaned: list[str] = []
    for tag in value:
        normalized = tag.strip()
        if normalized and normalized not in cleaned:
            cleaned.append(normalized)
    return cleaned


def _validate_date_string(value: str | None) -> str | None:
    if value is None:
        return value
    if not DATE_PATTERN.match(value):
        raise ValueError("date must use YYYY-MM-DD")
    date.fromisoformat(value)
    return value


class BookCreate(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True)

    title: str = Field(..., min_length=1)
    author: str | None = None
    cover_url: str | None = None
    category: str | None = None
    status: BookStatus = "wishlist"
    rating: float | None = Field(default=None, ge=1, le=10)
    tags: list[str] = Field(default_factory=list)
    short_comment: str | None = None
    review: str | None = None
    start_date: str | None = None
    finish_date: str | None = None

    @field_validator("title")
    @classmethod
    def validate_title(cls, value: str) -> str:
        title = value.strip()
        if not title:
            raise ValueError("title is required")
        return title

    @field_validator(
        "author",
        "cover_url",
        "category",
        "short_comment",
        "review",
        "start_date",
        "finish_date",
        mode="before",
    )
    @classmethod
    def normalize_optional_text(cls, value: object) -> object:
        return _empty_to_none(value)

    @field_validator("rating")
    @classmethod
    def validate_rating(cls, value: float | None) -> float | None:
        return _validate_rating(value)

    @field_validator("tags")
    @classmethod
    def validate_tags(cls, value: list[str]) -> list[str]:
        return _validate_tags(value) or []

    @field_validator("start_date", "finish_date")
    @classmethod
    def validate_dates(cls, value: str | None) -> str | None:
        return _validate_date_string(value)


class BookUpdate(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True)

    title: str | None = None
    author: str | None = None
    cover_url: str | None = None
    category: str | None = None
    status: BookStatus | None = None
    rating: float | None = Field(default=None, ge=1, le=10)
    tags: list[str] | None = None
    short_comment: str | None = None
    review: str | None = None
    start_date: str | None = None
    finish_date: str | None = None

    @field_validator("title")
    @classmethod
    def validate_title(cls, value: str | None) -> str | None:
        if value is None:
            raise ValueError("title cannot be null")
        title = value.strip()
        if not title:
            raise ValueError("title cannot be empty")
        return title

    @field_validator(
        "author",
        "cover_url",
        "category",
        "short_comment",
        "review",
        "start_date",
        "finish_date",
        mode="before",
    )
    @classmethod
    def normalize_optional_text(cls, value: object) -> object:
        return _empty_to_none(value)

    @field_validator("rating")
    @classmethod
    def validate_rating(cls, value: float | None) -> float | None:
        return _validate_rating(value)

    @field_validator("tags")
    @classmethod
    def validate_tags(cls, value: list[str] | None) -> list[str] | None:
        return _validate_tags(value)

    @field_validator("start_date", "finish_date")
    @classmethod
    def validate_dates(cls, value: str | None) -> str | None:
        return _validate_date_string(value)


class BookOut(BaseModel):
    id: int
    title: str
    author: str | None = None
    cover_url: str | None = None
    category: str | None = None
    status: BookStatus
    rating: float | None = None
    tags: list[str]
    short_comment: str | None = None
    review: str | None = None
    start_date: str | None = None
    finish_date: str | None = None
    created_at: str
    updated_at: str
    is_deleted: bool
    deleted_at: str | None = None

