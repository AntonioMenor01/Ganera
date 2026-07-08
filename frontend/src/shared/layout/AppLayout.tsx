import { Outlet } from "react-router-dom";

export function AppLayout() {
  return (
    <div className="min-h-screen bg-background text-foreground">
      <header className="border-b px-6 py-4">
        <span className="font-semibold">Ganera</span>
      </header>
      <main className="p-6">
        <Outlet />
      </main>
    </div>
  );
}
