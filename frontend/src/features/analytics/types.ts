export interface Overview {
  totalRecords: number;
  totalAmount: number;
  averageAmount: number;
  categories: number;
  source: 'summary' | 'direct';
}

export interface MonthlyPoint {
  month: string;
  totalAmount: number;
  txCount: number;
}

export interface CategoryPoint {
  category: string;
  totalAmount: number;
  txCount: number;
}

export interface MonthRange {
  from: string;
  to: string;
}
