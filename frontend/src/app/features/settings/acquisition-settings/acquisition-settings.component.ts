import {ChangeDetectorRef, Component, inject, OnInit} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {Button} from 'primeng/button';
import {TableModule} from 'primeng/table';
import {Dialog} from 'primeng/dialog';
import {InputText} from 'primeng/inputtext';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {Select} from 'primeng/select';
import {MessageService} from 'primeng/api';
import {Toast} from 'primeng/toast';
import {Tooltip} from 'primeng/tooltip';
import {AcquisitionService, DownloadClient, Indexer} from '../../../core/services/acquisition.service';

@Component({
  selector: 'app-acquisition-settings',
  imports: [
    FormsModule,
    Button,
    TableModule,
    Dialog,
    InputText,
    ToggleSwitch,
    Select,
    Toast,
    Tooltip,
  ],
  providers: [MessageService],
  templateUrl: './acquisition-settings.component.html',
  styleUrl: './acquisition-settings.component.scss'
})
export class AcquisitionSettingsComponent implements OnInit {

  private readonly acquisitionService = inject(AcquisitionService);
  private readonly messageService = inject(MessageService);
  private readonly cdr = inject(ChangeDetectorRef);

  indexers: Indexer[] = [];
  clients: DownloadClient[] = [];

  showIndexerDialog = false;
  showClientDialog = false;
  editingIndexer: Partial<Indexer> = {};
  editingClient: Partial<DownloadClient> = {};
  isEditIndexer = false;
  isEditClient = false;

  testingIndexerId: number | null = null;
  testingClientId: number | null = null;
  showIndexerKey = false;
  showClientKey = false;

  clientTypeOptions = [{label: 'SABnzbd', value: 'SABNZBD'}];

  ngOnInit(): void {
    this.loadIndexers();
    this.loadClients();
  }

  loadIndexers(): void {
    this.acquisitionService.getIndexers().subscribe({
      next: (data) => {
        this.indexers = data;
        this.cdr.detectChanges();
      },
      error: () => this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to load indexers'})
    });
  }

  loadClients(): void {
    this.acquisitionService.getClients().subscribe({
      next: (data) => {
        this.clients = data;
        this.cdr.detectChanges();
      },
      error: () => this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to load clients'})
    });
  }

  openAddIndexerDialog(): void {
    this.editingIndexer = {enabled: true, priority: 0};
    this.isEditIndexer = false;
    this.showIndexerKey = false;
    this.showIndexerDialog = true;
  }

  openEditIndexerDialog(indexer: Indexer): void {
    this.editingIndexer = {...indexer};
    this.isEditIndexer = true;
    this.showIndexerKey = false;
    this.showIndexerDialog = true;
  }

  saveIndexer(): void {
    if (!this.editingIndexer.name || !this.editingIndexer.url) {
      this.messageService.add({severity: 'warn', summary: 'Validation', detail: 'Name and URL are required'});
      return;
    }
    const indexer = this.editingIndexer as Indexer;
    if (this.isEditIndexer && indexer.id != null) {
      this.acquisitionService.updateIndexer(indexer.id, indexer).subscribe({
        next: () => {
          this.showIndexerDialog = false;
          this.loadIndexers();
          this.messageService.add({severity: 'success', summary: 'Saved', detail: 'Indexer updated'});
        },
        error: () => this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to update indexer'})
      });
    } else {
      this.acquisitionService.createIndexer(indexer).subscribe({
        next: () => {
          this.showIndexerDialog = false;
          this.loadIndexers();
          this.messageService.add({severity: 'success', summary: 'Saved', detail: 'Indexer created'});
        },
        error: () => this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to create indexer'})
      });
    }
  }

  deleteIndexer(indexer: Indexer): void {
    if (!confirm(`Delete indexer "${indexer.name}"?`)) return;
    this.acquisitionService.deleteIndexer(indexer.id!).subscribe({
      next: () => {
        this.loadIndexers();
        this.messageService.add({severity: 'success', summary: 'Deleted', detail: 'Indexer removed'});
      },
      error: () => this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to delete indexer'})
    });
  }

  testIndexer(indexer: Indexer): void {
    this.testingIndexerId = indexer.id!;
    this.acquisitionService.testIndexer(indexer.id!).subscribe({
      next: (result) => {
        this.testingIndexerId = null;
        if (result.success) {
          this.messageService.add({severity: 'success', summary: 'Connected', detail: result.message});
        } else {
          this.messageService.add({severity: 'error', summary: 'Failed', detail: result.message});
        }
      },
      error: (err) => {
        this.testingIndexerId = null;
        this.messageService.add({severity: 'error', summary: 'Error', detail: err.error?.message || 'Connection test failed'});
      }
    });
  }

  openAddClientDialog(): void {
    this.editingClient = {enabled: true, type: 'SABNZBD', category: 'books'};
    this.isEditClient = false;
    this.showClientKey = false;
    this.showClientDialog = true;
  }

  openEditClientDialog(client: DownloadClient): void {
    this.editingClient = {...client};
    this.isEditClient = true;
    this.showClientKey = false;
    this.showClientDialog = true;
  }

  saveClient(): void {
    if (!this.editingClient.name || !this.editingClient.url) {
      this.messageService.add({severity: 'warn', summary: 'Validation', detail: 'Name and URL are required'});
      return;
    }
    const client = this.editingClient as DownloadClient;
    if (this.isEditClient && client.id != null) {
      this.acquisitionService.updateClient(client.id, client).subscribe({
        next: () => {
          this.showClientDialog = false;
          this.loadClients();
          this.messageService.add({severity: 'success', summary: 'Saved', detail: 'Client updated'});
        },
        error: () => this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to update client'})
      });
    } else {
      this.acquisitionService.createClient(client).subscribe({
        next: () => {
          this.showClientDialog = false;
          this.loadClients();
          this.messageService.add({severity: 'success', summary: 'Saved', detail: 'Client created'});
        },
        error: () => this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to create client'})
      });
    }
  }

  deleteClient(client: DownloadClient): void {
    if (!confirm(`Delete client "${client.name}"?`)) return;
    this.acquisitionService.deleteClient(client.id!).subscribe({
      next: () => {
        this.loadClients();
        this.messageService.add({severity: 'success', summary: 'Deleted', detail: 'Client removed'});
      },
      error: () => this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to delete client'})
    });
  }

  testClient(client: DownloadClient): void {
    this.testingClientId = client.id!;
    this.acquisitionService.testClient(client.id!).subscribe({
      next: (result) => {
        this.testingClientId = null;
        if (result.success) {
          this.messageService.add({severity: 'success', summary: 'Connected', detail: result.message});
        } else {
          this.messageService.add({severity: 'error', summary: 'Failed', detail: result.message});
        }
      },
      error: (err) => {
        this.testingClientId = null;
        this.messageService.add({severity: 'error', summary: 'Error', detail: err.error?.message || 'Connection test failed'});
      }
    });
  }
}
