import { CompanyResearchRequest, CompanyResearchResponse } from '@/types/research';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

export const researchService = {
  async researchCompany(request: CompanyResearchRequest): Promise<CompanyResearchResponse> {
    console.log('Research request:', request);

    const token = sessionStorage.getItem('authToken');
    
    try {
      const response = await fetch(`${API_BASE_URL}api/analyze`, {
        method: 'POST',
        credentials: "include",
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`,
        },
        body: JSON.stringify(request),
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const data = await response.json();
      console.log('Research response:', data);

      return {
        success: true,
        data: {
          company: request.company,
          info: data.info || data
        }
      };
    } catch (error) {
      console.error('Research error:', error);
      return {
        success: false,
        message: 'Failed to research company. Please try again.'
      };
    }
  }
};
