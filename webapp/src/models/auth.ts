import { create } from 'zustand';
import { clearSession, getToken } from '@/service/base';

/** 登录态（token 在 localStorage，用户名内存态即可）。umi 数据流约定 default export hook。 */
interface AuthState {
  username: string | null;
  bootstrap: (saved: string | null) => void;
  setUsername: (username: string) => void;
  clear: () => void;
}

const useAuthModel = create<AuthState>((set) => ({
  username: null,
  bootstrap: (saved) => {
    if (getToken() && saved) {
      set({ username: saved });
    }
  },
  setUsername: (username) => set({ username }),
  clear: () => {
    clearSession();
    set({ username: null });
  },
}));

export default useAuthModel;
