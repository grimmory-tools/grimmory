import {Observable, firstValueFrom} from 'rxjs';
import {skip, take} from 'rxjs/operators';

interface ChartTooltipContextInput {
  data: number[];
  labels: string[];
  dataIndex: number;
}

export function nextChartEmission<T>(source$: Observable<T>): Promise<T> {
  return firstValueFrom(source$.pipe(skip(1), take(1)));
}

export function fakeChartTooltipContext(input: ChartTooltipContextInput) {
  return {
    dataIndex: input.dataIndex,
    dataset: {
      data: input.data,
    },
    chart: {
      data: {
        labels: input.labels,
      },
    },
  };
}
