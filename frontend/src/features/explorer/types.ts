export interface Transaction {
  id: number;
  externalId: string;
  occurredAt: string;
  category: string;
  description: string | null;
  amount: number;
  source: string | null;
}

export interface TransactionPage {
  data: Transaction[];
  nextCursor: string | null;
  hasMore: boolean;
}

export interface Filters {
  category?: string;
  from?: string;
  to?: string;
  jobId?: string;
}
