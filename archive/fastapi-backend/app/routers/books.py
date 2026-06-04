from fastapi import APIRouter, HTTPException, Query, status

from .. import crud
from ..schemas import BookCreate, BookOut, BookStatus, BookUpdate

router = APIRouter(prefix="/api/books", tags=["books"])


@router.get("", response_model=list[BookOut])
def list_books(
    status_filter: BookStatus | None = Query(default=None, alias="status"),
    keyword: str | None = None,
    tag: str | None = None,
) -> list[dict]:
    return crud.list_books(status=status_filter, keyword=keyword, tag=tag)


@router.get("/{book_id}", response_model=BookOut)
def get_book(book_id: int) -> dict:
    book = crud.get_book(book_id)
    if book is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="book not found")
    return book


@router.post("", response_model=BookOut, status_code=status.HTTP_201_CREATED)
def create_book(payload: BookCreate) -> dict:
    return crud.create_book(payload)


@router.put("/{book_id}", response_model=BookOut)
def update_book(book_id: int, payload: BookUpdate) -> dict:
    book = crud.update_book(book_id, payload)
    if book is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="book not found")
    return book


@router.patch("/{book_id}/archive", response_model=BookOut)
def archive_book(book_id: int) -> dict:
    book = crud.archive_book(book_id)
    if book is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="book not found")
    return book

