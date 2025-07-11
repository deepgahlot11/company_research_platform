

export interface CompanyResearchRequest {
  company: string;
  extraction_schema?: Record<string, string>;
  user_notes?: string;
}

export interface CompanyResearchResponse {
  success: boolean;
  data?: {
    company: string;
    info: Record<string, any>;
  };
  message?: string;
}

export interface RealTimeUpdatesProps {
  company: string;
  userNotes?: string;
  onComplete: (data: any) => void;
  onError: (error: string) => void;
  isActive: boolean;
}

