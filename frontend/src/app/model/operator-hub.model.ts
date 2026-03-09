export interface OperatorHubOperator {
  name: string;
  installKey?: string | null;
  iconUrl?: string | null;
  installYamlUrl?: string;
}

export interface OperatorHubListResponse {
  items: OperatorHubOperator[];
  page: number;
  size: number;
  total: number;
  query?: string | null;
}
