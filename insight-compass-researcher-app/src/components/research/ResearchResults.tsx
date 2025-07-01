import React from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { CompanyResearchResponse } from "@/types/research";
import { Building, Download } from "lucide-react";
import jsPDF from "jspdf";

interface ResearchResultsProps {
  result: CompanyResearchResponse | null;
  isLoading: boolean;
  companyName?: string;
}

export const ResearchResults: React.FC<ResearchResultsProps> = ({
  result,
  isLoading,
  companyName,
}) => {
  const renderValue = (value: any): string => {
    if (typeof value === "object" && value !== null) {
      return JSON.stringify(value, null, 2);
    }
    return String(value);
  };

  const formatKey = (key: string): string => {
    return key
      .split("_")
      .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
      .join(" ");
  };

  const exportToPDF = () => {
    if (!result?.success || !result.data) return;

    const doc = new jsPDF();
    const data = result.data;
    const pageHeight = doc.internal.pageSize.height;
    const marginLeft = 20;
    const marginRight = 20;
    const maxWidth = doc.internal.pageSize.width - marginLeft - marginRight;

    // Add title
    doc.setFontSize(20);
    doc.setFont(undefined, "bold");
    doc.text(data.company, marginLeft, 30);

    // Add content
    let yPosition = 50;
    doc.setFontSize(12);
    doc.setFont(undefined, "normal");

    if (data.info && Object.keys(data.info).length > 0) {
      Object.entries(data.info).forEach(([key, value]) => {
        // Check if we need a new page before adding content
        if (yPosition > pageHeight - 40) {
          doc.addPage();
          yPosition = 30;
        }

        // Add key (formatted)
        doc.setFont(undefined, "bold");
        const keyLines = doc.splitTextToSize(formatKey(key), maxWidth);
        doc.text(keyLines, marginLeft, yPosition);
        yPosition += keyLines.length * 7 + 5;

        // Add value
        doc.setFont(undefined, "normal");
        const valueText = renderValue(value);
        const valueLines = doc.splitTextToSize(valueText, maxWidth);

        // Check if value content would exceed page
        const contentHeight = valueLines.length * 6;
        if (yPosition + contentHeight > pageHeight - 30) {
          doc.addPage();
          yPosition = 30;
        }

        doc.text(valueLines, marginLeft, yPosition);
        yPosition += valueLines.length * 6 + 15;
      });
    }

    // Save the PDF
    doc.save(
      `${data.company.replace(/[^a-z0-9]/gi, "_").toLowerCase()}_research.pdf`
    );
  };

  if (isLoading) {
    return (
      <Card className="h-full">
        <CardHeader>
          <CardTitle>Research Results</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex items-center justify-center h-64">
            <div className="text-center">
              <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary mx-auto mb-4"></div>
              <p className="text-muted-foreground">
                Researching about {companyName || "company"}...
              </p>
            </div>
          </div>
        </CardContent>
      </Card>
    );
  }

  if (!result) {
    return (
      <Card className="h-full">
        <CardHeader>
          <CardTitle>Research Results</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex items-center justify-center h-64">
            <p className="text-muted-foreground">
              Enter a company name to start researching
            </p>
          </div>
        </CardContent>
      </Card>
    );
  }

  if (!result.success) {
    return (
      <Card className="h-full">
        <CardHeader>
          <CardTitle>Research Results</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex items-center justify-center h-64">
            <p className="text-destructive">
              {result.message || "Failed to research company"}
            </p>
          </div>
        </CardContent>
      </Card>
    );
  }

  const data = result.data!;

  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Building className="h-5 w-5" />
            {data.company}
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={exportToPDF}
            className="flex items-center gap-2"
          >
            <Download className="h-4 w-4" />
            Export PDF
          </Button>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {data.info && Object.keys(data.info).length > 0 ? (
          <div className="space-y-4">
            {Object.entries(data.info).map(([key, value]) => (
              <div
                key={key}
                className="border-b border-gray-100 pb-3 last:border-b-0"
              >
                <h3 className="font-semibold text-sm text-gray-700 mb-1">
                  {formatKey(key)}
                </h3>
                <p className="text-sm text-gray-900 whitespace-pre-wrap">
                  {renderValue(value)}
                </p>
              </div>
            ))}
          </div>
        ) : (
          <div className="flex items-center justify-center h-32">
            <p className="text-muted-foreground">No information available</p>
          </div>
        )}
      </CardContent>
    </Card>
  );
};
