# ReadTrace Backend

FastAPI 后端，使用 SQLite 保存 v1.0 书籍记录数据。

## API

- `GET /api/health`
- `GET /api/books?status=&keyword=&tag=`
- `GET /api/books/{book_id}`
- `POST /api/books`
- `PUT /api/books/{book_id}`
- `PATCH /api/books/{book_id}/archive`

## 数据规则

- `tags` 在 SQLite 中保存为 JSON 字符串，API 返回数组
- `rating` 可为空；填写时必须在 1 到 10 之间，最多一位小数
- 归档使用软删除：`is_deleted = 1`，并写入 `deleted_at`
- 默认列表和详情不返回已归档书籍

