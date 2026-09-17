import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';
import type { DataSourceProfile, ExecuteResult, PreviewResult } from '@/service/api';

export type WorkspaceView =
  | { type: 'objects'; profile: DataSourceProfile; namespace: string; objectType: 'TABLE' | 'VIEW' }
  | { type: 'data'; profile: DataSourceProfile; namespace: string; table: string }
  | { type: 'structure'; profile: DataSourceProfile; namespace: string; table: string }
  | {
      type: 'query';
      profile: DataSourceProfile;
      namespace?: string;
      table?: string;
      initialSql?: string;
      sessionKey: string;
    };

export interface WorkspaceTab {
  key: string;
  title: string;
  view: WorkspaceView;
}

export interface QuerySession {
  datasourceId: number | null;
  namespace: string | null;
  sql: string;
  maxRows: number;
  pendingSql: string | null;
  preview: PreviewResult | null;
  confirming: boolean;
  executing: boolean;
  result: ExecuteResult | null;
}

interface WorkspaceState {
  tabs: WorkspaceTab[];
  activeKey?: string;
  querySequence: number;
  querySessions: Record<string, QuerySession>;
  addTab: (tab: WorkspaceTab) => void;
  closeTab: (key: string) => void;
  setActiveKey: (key?: string) => void;
  createQueryTab: (
    profile: DataSourceProfile,
    options?: { namespace?: string; table?: string; initialSql?: string; fixedKey?: string },
  ) => string;
  ensureQuerySession: (key: string, initialDatasourceId?: number, initialSql?: string) => void;
  updateQuerySession: (key: string, patch: Partial<QuerySession>) => void;
}

const newQuerySession = (initialDatasourceId?: number, initialSql?: string, initialNamespace?: string): QuerySession => ({
  datasourceId: initialDatasourceId ?? null,
  namespace: initialNamespace ?? null,
  sql: initialSql ?? 'SELECT 1',
  maxRows: 1000,
  pendingSql: null,
  preview: null,
  confirming: false,
  executing: false,
  result: null,
});

export const useWorkspaceModel = create<WorkspaceState>()(
  persist(
    (set, get) => ({
      tabs: [],
      activeKey: undefined,
      querySequence: 0,
      querySessions: {},

      addTab: (tab) => set((state) => ({
        tabs: state.tabs.some((item) => item.key === tab.key) ? state.tabs : [...state.tabs, tab],
        activeKey: tab.key,
      })),

      closeTab: (key) => set((state) => {
        const targetIndex = state.tabs.findIndex((item) => item.key === key);
        const tabs = state.tabs.filter((item) => item.key !== key);
        const querySessions = { ...state.querySessions };
        delete querySessions[key];
        return {
          tabs,
          querySessions,
          activeKey: state.activeKey === key
            ? tabs[Math.max(0, targetIndex - 1)]?.key
            : state.activeKey,
        };
      }),

      setActiveKey: (activeKey) => set({ activeKey }),

      createQueryTab: (profile, options = {}) => {
        const sequence = options.fixedKey ? get().querySequence : get().querySequence + 1;
        const key = options.fixedKey ?? `query:${profile.id}:${sequence}`;
        const title = options.namespace ? `查询 - ${options.namespace}` : `查询 - ${profile.name}`;
        const view: WorkspaceView = {
          type: 'query',
          profile,
          namespace: options.namespace,
          table: options.table,
          initialSql: options.initialSql,
          sessionKey: key,
        };
        set((state) => ({
          tabs: state.tabs.some((item) => item.key === key)
            ? state.tabs
            : [...state.tabs, { key, title, view }],
          activeKey: key,
          querySequence: sequence,
          querySessions: state.querySessions[key]
            ? state.querySessions
            : {
                ...state.querySessions,
                [key]: newQuerySession(profile.id, options.initialSql, options.namespace),
              },
        }));
        return key;
      },

      ensureQuerySession: (key, initialDatasourceId, initialSql) => set((state) => (
        state.querySessions[key]
          ? state
          : {
              querySessions: {
                ...state.querySessions,
                [key]: newQuerySession(initialDatasourceId, initialSql),
              },
            }
      )),

      updateQuerySession: (key, patch) => set((state) => ({
        querySessions: {
          ...state.querySessions,
          [key]: { ...(state.querySessions[key] ?? newQuerySession()), ...patch },
        },
      })),
    }),
    {
      name: 'meper.workspace.state.v1',
      storage: createJSONStorage(() => sessionStorage),
      partialize: (state) => ({
        tabs: state.tabs,
        activeKey: state.activeKey,
        querySequence: state.querySequence,
        querySessions: Object.fromEntries(
          Object.entries(state.querySessions).map(([key, session]) => [key, {
            ...session,
            pendingSql: null,
            preview: null,
            confirming: false,
            executing: false,
            result: null,
          }]),
        ),
      }),
    },
  ),
);
