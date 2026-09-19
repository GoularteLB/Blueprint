import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { formatCurrency, formatInteger, formatMonth } from '../../../shared/format';
import type { MonthlyPoint } from '../types';

interface Props {
  data: MonthlyPoint[];
}

const compactCurrency = new Intl.NumberFormat('pt-BR', { notation: 'compact', maximumFractionDigits: 1 });

export function MonthlyChart({ data }: Props) {
  return (
    <div className="h-72 w-full text-slate-500 dark:text-slate-400">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 8 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="currentColor" strokeOpacity={0.2} vertical={false} />
          <XAxis dataKey="month" tickFormatter={formatMonth} stroke="currentColor" fontSize={12} />
          <YAxis
            tickFormatter={(value: number) => compactCurrency.format(value)}
            stroke="currentColor"
            fontSize={12}
            width={56}
          />
          <Tooltip
            formatter={(value: number, name: string) =>
              name === 'totalAmount' ? [formatCurrency(value), 'Total'] : [formatInteger(value), 'Transações']
            }
            labelFormatter={formatMonth}
          />
          <Bar dataKey="totalAmount" fill="#4f46e5" radius={[4, 4, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
