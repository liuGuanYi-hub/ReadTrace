import { useEffect, useState } from "react";

import AddBookPage from "./pages/AddBookPage";
import BookDetailPage from "./pages/BookDetailPage";
import EditBookPage from "./pages/EditBookPage";
import HomePage from "./pages/HomePage";

type Route =
  | { name: "home" }
  | { name: "new" }
  | { name: "detail"; bookId: number }
  | { name: "edit"; bookId: number };

function parseRoute(): Route {
  const hash = window.location.hash.replace(/^#/, "") || "/";
  const parts = hash.split("/").filter(Boolean);

  if (parts.length === 0) {
    return { name: "home" };
  }

  if (parts[0] === "books" && parts[1] === "new") {
    return { name: "new" };
  }

  if (parts[0] === "books" && parts[1]) {
    const bookId = Number(parts[1]);
    if (Number.isInteger(bookId) && bookId > 0 && parts[2] === "edit") {
      return { name: "edit", bookId };
    }
    if (Number.isInteger(bookId) && bookId > 0) {
      return { name: "detail", bookId };
    }
  }

  return { name: "home" };
}

function navigate(path: string) {
  window.location.hash = path;
}

export default function App() {
  const [route, setRoute] = useState<Route>(() => parseRoute());

  useEffect(() => {
    const onHashChange = () => setRoute(parseRoute());
    window.addEventListener("hashchange", onHashChange);
    if (!window.location.hash) {
      window.location.hash = "/";
    }
    return () => window.removeEventListener("hashchange", onHashChange);
  }, []);

  return (
    <main className="app-shell">
      {route.name === "home" && <HomePage navigate={navigate} />}
      {route.name === "new" && <AddBookPage navigate={navigate} />}
      {route.name === "detail" && <BookDetailPage bookId={route.bookId} navigate={navigate} />}
      {route.name === "edit" && <EditBookPage bookId={route.bookId} navigate={navigate} />}
    </main>
  );
}

