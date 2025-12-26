export interface KubePodEvent {
  type?: string;
  reason?: string;
  message?: string;
  timestamp?: string;
  count?: number;
}

