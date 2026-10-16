import {computed, inject, Service, DestroyRef} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {HttpClient} from '@angular/common/http';
import {lastValueFrom} from 'rxjs';
import {RxStompService} from '../../../shared/websocket/rx-stomp.service';
import {API_CONFIG} from '../../../core/config/api-config';
import {injectQuery, queryOptions, QueryClient} from '@tanstack/angular-query-experimental';

const LIBRARY_HEALTH_QUERY_KEY = ['libraryHealth'] as const;

@Service()
export class LibraryHealthService {
  private readonly http = inject(HttpClient);
  private readonly rxStompService = inject(RxStompService);
  private readonly queryClient = inject(QueryClient);
  private readonly destroyRef = inject(DestroyRef);
  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/libraries/health`;

  private socketInitialized = false;

  private readonly healthQuery = injectQuery(() => ({
    ...this.getHealthQueryOptions(),
    enabled: false, // manually triggered via initialize()
  }));

  readonly health = computed(() => this.healthQuery.data() ?? {});

  private getHealthQueryOptions() {
    return queryOptions({
      queryKey: LIBRARY_HEALTH_QUERY_KEY,
      queryFn: () => lastValueFrom(this.http.get<Record<number, boolean>>(this.url))
    });
  }

  fetchHealth(): void {
    void this.queryClient.query(this.getHealthQueryOptions());
  }

  initWebsocket(): void {
    if (this.socketInitialized) return;

    this.rxStompService.watch('/topic/library-health')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(msg => {
        const payload = JSON.parse(msg.body);
        this.queryClient.setQueryData(LIBRARY_HEALTH_QUERY_KEY, payload.libraryHealth);
      });
    this.socketInitialized = true;
  }

  isUnhealthy(libraryId: number): boolean {
    return this.health()[libraryId] === false;
  }
}
