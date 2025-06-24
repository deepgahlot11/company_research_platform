import React, { useState } from 'react';
import { Button } from '@/components/ui/button';
import { ResearchInput } from '@/components/research/ResearchInput';
import { ResearchResults } from '@/components/research/ResearchResults';
import { useAuth } from '@/contexts/AuthContext';
import { researchService } from '@/services/researchService';
import { authService } from '@/services/authService';
import { CompanyResearchRequest, CompanyResearchResponse } from '@/types/research';
import { toast } from '@/hooks/use-toast';
import { LogOut, User } from 'lucide-react';

const Dashboard = () => {
  const { user, logout } = useAuth();
  const [isResearching, setIsResearching] = useState(false);
  const [researchResult, setResearchResult] = useState<CompanyResearchResponse | null>(null);
  const [currentCompanyName, setCurrentCompanyName] = useState<string>('');

  const handleResearch = async (request: CompanyResearchRequest) => {
    setIsResearching(true);
    setResearchResult(null);
    setCurrentCompanyName(request.company);

    try {
      const response = await researchService.researchCompany(request);
      setResearchResult(response);
      
      if (response.success) {
        toast({
          title: "Research completed",
          description: `Found information about ${request.company}`,
        });
      }
    } catch (error) {
      toast({
        title: "Research failed",
        description: "An error occurred while researching the company",
        variant: "destructive",
      });
    } finally {
      setIsResearching(false);
    }
  };

  const handleLogout = async () => {
    try {
      await authService.logout();
      logout();
      toast({
        title: "Logged out",
        description: "You have been successfully logged out",
      });
    } catch (error) {
      toast({
        title: "Error",
        description: "An error occurred during logout",
        variant: "destructive",
      });
    }
  };

  return (
    <div className="min-h-screen bg-background">
      {/* Header */}
      <header className="border-b bg-white">
        <div className="container mx-auto px-4 py-4 flex justify-between items-center">
          <h1 className="text-2xl font-bold">Company Researcher</h1>
          <div className="flex items-center gap-4">
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <User className="h-4 w-4" />
              <span>{user?.firstName} {user?.lastName}</span>
            </div>
            <Button variant="outline" size="sm" onClick={handleLogout}>
              <LogOut className="h-4 w-4 mr-2" />
              Logout
            </Button>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="container mx-auto px-4 py-6">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 h-[calc(100vh-8rem)]">
          {/* Left Pane - Input */}
          <div className="order-2 lg:order-1">
            <ResearchInput onResearch={handleResearch} isLoading={isResearching} />
          </div>
          
          {/* Right Pane - Results */}
          <div className="order-1 lg:order-2">
            <ResearchResults 
              result={researchResult} 
              isLoading={isResearching} 
              companyName={currentCompanyName}
            />
          </div>
        </div>
      </main>
    </div>
  );
};

export default Dashboard;
