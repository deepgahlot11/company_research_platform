import React, { useState } from "react";
import { Button } from "@/components/ui/button";
import { ResearchInput } from "@/components/research/ResearchInput";
import { ResearchResults } from "@/components/research/ResearchResults";
import { RealTimeUpdates } from "@/components/research/RealTimeUpdates";
import { useAuth } from "@/contexts/AuthContext";
import { authService } from "@/services/authService";
import {
  CompanyResearchRequest,
  CompanyResearchResponse,
} from "@/types/research";
import { toast } from "@/hooks/use-toast";
import { LogOut, User } from "lucide-react";

const Dashboard = () => {
  const { user, logout } = useAuth();
  const [isResearching, setIsResearching] = useState(false);
  const [researchResult, setResearchResult] =
    useState<CompanyResearchResponse | null>(null);
  const [currentCompanyName, setCurrentCompanyName] = useState<string>("");
  const [currentUserNotes, setCurrentUserNotes] = useState<string>("");
  const [currentExtractionSchema, setCurrentExtractionSchema] = useState<
    Record<string, any> | undefined
  >(undefined);
  const [streamKey, setStreamKey] = useState<number>(0); // Force refresh of RealTimeUpdates

  const handleResearch = async (request: CompanyResearchRequest) => {
    setIsResearching(true);
    setResearchResult(null);
    setCurrentCompanyName(request.company);
    setCurrentUserNotes(request.user_notes || "");
    setCurrentExtractionSchema(request.extraction_schema);
    setStreamKey((prev) => prev + 1); // Force new stream component
  };

  const handleResearchComplete = (data: any) => {
    const response: CompanyResearchResponse = {
      success: true,
      data: {
        company: currentCompanyName,
        info: data.info || data,
      },
    };

    setResearchResult(response);
    setIsResearching(false);

    toast({
      title: "Research completed",
      description: `Found information about ${currentCompanyName}`,
    });
  };

  const handleResearchError = (error: string) => {
    const response: CompanyResearchResponse = {
      success: false,
      message: error,
    };

    setResearchResult(response);
    setIsResearching(false);

    toast({
      title: "Research failed",
      description: error,
      variant: "destructive",
    });
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
          <h1 className="text-2xl font-bold">Company Research Platform</h1>
          <div className="flex items-center gap-4">
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <User className="h-4 w-4" />
              <span>
                {user?.firstName} {user?.lastName}
              </span>
            </div>
            <Button variant="outline" size="sm" onClick={handleLogout}>
              <LogOut className="h-4 w-4 mr-2" />
              Logout
            </Button>
          </div>
        </div>
      </header>

      {/* Main Content - 3 Column Layout */}
      <main className="container mx-auto px-4 py-6">
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 h-[calc(100vh-8rem)]">
          {/* Left Pane - Input */}
          <div className="lg:col-span-1">
            <ResearchInput
              onResearch={handleResearch}
              isLoading={isResearching}
            />
          </div>

          {/* Middle Pane - Real-time Timeline */}
          <div className="lg:col-span-1">
            <RealTimeUpdates
              key={streamKey} // Force component refresh for new streams
              company={currentCompanyName}
              userNotes={currentUserNotes}
              extractionSchema={currentExtractionSchema}
              onComplete={handleResearchComplete}
              onError={handleResearchError}
              isActive={isResearching}
            />
          </div>

          {/* Right Pane - Results */}
          <div className="lg:col-span-1">
            <ResearchResults
              result={researchResult}
              isLoading={false}
              companyName={currentCompanyName}
            />
          </div>
        </div>
      </main>
    </div>
  );
};

export default Dashboard;
