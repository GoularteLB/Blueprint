import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { formatCurrency, formatInteger } from '../../../shared/format';
import type { CategoryPoint } from '../types';

interface Props {
  data: CategoryPoint[];
}

const compactCurrency = new Intl.NumberFormat('pt-BR', { notation: 'compact', maximumFractionDigits: 1 });

export function CategoryChart({ data }: Props) {
  return (
    <div className="h-72 w-full text-slate-500 dark:text-slate-400">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} layout="vertical" margin={{ top: 8, right: 16, bottom: 0, left: 8 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="currentColor" strokeOpacity={0.2} horizontal={false} />
          <XAxis
            type="number"
            tickFormatter={(value: number) => compactCurrency.format(value)}
            stroke="currentColor"
            fontSize={12}
          />
          <YAxis type="category" dataKey="category" stroke="currentColor" fontSize={12} width={90} />
          <Tooltip
            formatter={(value: number, name: string) =>
              name === 'totalAmount' ? [formatCurrency(value), 'Total'] : [formatInteger(value), 'Transações']
            }
          />
          <Bar dataKey="totalAmount" fill="#0d9488" radius={[0, 4, 4, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
