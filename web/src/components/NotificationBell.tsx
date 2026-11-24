// Updated NotificationBell with proper error handling and unread refresh
import { useEffect, useRef, useState } from 'react';
import { Bell } from 'lucide-react';
import {
  getUnreadCount,
  getMyNotifications,
  markAsRead,
  markAllAsRead,
  deleteNotification,
} from '../services/notificationService';
import { hasPermission } from '../services/authService';

export default function NotificationBell() {
  const [open, setOpen] = useState(false);
  const [unread, setUnread] = useState(0);
  const [list, setList] = useState([]);
  const dropdownRef = useRef<HTMLDivElement>(null);

  const loadUnread = async () => {
    try {
      const { data } = await getUnreadCount();
      setUnread(data.count);
    } catch (e) {
      console.error('Unread count error:', e);
      alert('Failed to load unread notification count');
    }
  };

  const loadLatest = async () => {
    try {
      const { data } = await getMyNotifications(0, 5);
      setList(data.content);
    } catch (e) {
      console.error('Load latest notifications error:', e);
      alert('Failed to load notifications');
      setList([]);
    }
  };

  const toggle = async () => {
    setOpen(!open);
    if (!open) {
      await loadLatest();
      await loadUnread();
    }
  };

  const handleMarkAll = async () => {
    try {
      await markAllAsRead();
      await loadUnread();
      setList((prev) => prev.map((n) => ({ ...n, unread: false })));
    } catch (e) {
      console.error('Mark all read error:', e);
      alert('Failed to mark all as read');
    }
  };

  const handleMarkOne = async (id) => {
    try {
      await markAsRead(id);
      await loadUnread();
      setList((prev) =>
        prev.map((n) => (n.id === id ? { ...n, unread: false, readAt: new Date().toISOString() } : n))
      );
    } catch (e) {
      console.error('Mark one read error:', e);
      alert('Failed to mark notification as read');
    }
  };

  const handleDelete = async (id) => {
    try {
      await deleteNotification(id);
      await loadUnread();
      setList((prev) => prev.filter((n) => n.id !== id));
    } catch (e) {
      console.error('Delete notification error:', e);
      alert('Failed to delete notification');
    }
  };

  // close when clicking outside
  useEffect(() => {
    const onClickOutside = (e) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', onClickOutside);
    // initial load
    loadUnread();
    return () => document.removeEventListener('mousedown', onClickOutside);
  }, []);

  if (!hasPermission('PERM_notification:read')) return null;

  return (
    <div className="relative" ref={dropdownRef}>
      <button
        onClick={toggle}
        className="relative p-2 text-gray-600 hover:text-gray-900 rounded-full hover:bg-gray-100 focus:outline-none"
        aria-label="Notifications"
      >
        <Bell className="w-6 h-6" />
        {unread > 0 && (
          <span className="absolute top-1 right-1 flex items-center justify-center w-4 h-4 text-xs font-bold text-white bg-red-500 rounded-full">
            {unread}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 mt-2 w-80 bg-white rounded-lg shadow-lg border border-gray-200 z-50">
          <div className="flex items-center justify-between px-4 py-3 bg-gray-50 border-b border-gray-100">
            <h3 className="text-sm font-semibold text-gray-700">Notifications</h3>
            {unread > 0 && (
              <button
                onClick={handleMarkAll}
                className="text-xs text-blue-600 hover:text-blue-800 font-medium"
              >
                Mark all as read
              </button>
            )}
          </div>
          <div className="max-h-80 overflow-y-auto divide-y divide-gray-100">
            {list.length === 0 && (
              <div className="px-4 py-6 text-center text-sm text-gray-500">No notifications</div>
            )}
            {list.map((n) => (
              <div
                key={n.id}
                className={`px-4 py-3 text-sm transition-colors hover:bg-gray-50 ${n.unread ? 'bg-blue-50' : 'bg-white'}`}
              >
                <p className={n.unread ? 'font-semibold text-gray-900' : 'text-gray-600'}>{n.title}</p>
                <p className="text-xs text-gray-400 mt-1">{n.message}</p>
                <div className="mt-2 flex space-x-3 text-xs">
                  {n.unread && (
                    <button onClick={() => handleMarkOne(n.id)} className="text-blue-600 hover:underline">
                      Mark as read
                    </button>
                  )}
                  <button onClick={() => handleDelete(n.id)} className="text-red-600 hover:underline">
                    Delete
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
