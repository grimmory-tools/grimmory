import {computed, inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {lastValueFrom} from 'rxjs';
import {RxStompService} from '../../../shared/websocket/rx-stomp.service';
import {API_CONFIG} from '../../../core/config/api-config';
import {injectQuery, queryOptions, QueryClient} from '@tanstack/angular-query-experimental';

const LIBRARY_HEALTH_QUERY_KEY = ['libraryHealth'] as const;

@Injectable({providedIn: 'root'})
export class LibraryHealthService {
  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/libraries/health`;
  private http = inject(HttpClient);
  private rxStompService = inject(RxStompService);
  private queryClient = inject(QueryClient);

  private healthQuery = injectQuery(() => ({
    ...this.getHealthQueryOptions(),
    enabled: false, // manually triggered via initialize()
  }));

  health = computed(() => this.healthQuery.data() ?? {});

  private getHealthQueryOptions() {
    return queryOptions({
      queryKey: LIBRARY_HEALTH_QUERY_KEY,
      queryFn: () => lastValueFrom(this.http.get<Record<number, boolean>>(this.url))
    });
  }

  initialize(): void {
    void this.queryClient.fetchQuery(this.getHealthQueryOptions());

    this.rxStompService.watch('/topic/library-health').subscribe(msg => {
      const payload = JSON.parse(msg.body);
      this.queryClient.setQueryData(LIBRARY_HEALTH_QUERY_KEY, payload.libraryHealth);
    });
  }

  isUnhealthy(libraryId: number): boolean {
    return this.health()[libraryId] === false;
  }
}
