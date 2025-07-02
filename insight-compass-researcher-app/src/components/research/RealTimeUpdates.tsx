import React, { useState, useEffect, useRef } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { CheckCircle, Clock, Zap, AlertCircle, History } from "lucide-react";
import { createPortal } from "react-dom";
import { authService } from "@/services/authService";

interface RealTimeUpdatesProps {
  company: string;
  userNotes?: string;
  extractionSchema?: Record<string, any>;
  onComplete: (data: any) => void;
  onError: (error: string) => void;
  isActive: boolean;
}

interface UpdateItem {
  id: string;
  message: string;
  timestamp: Date;
  type: "info" | "success" | "warning" | "error";
  company?: string;
  data?: any;
}

interface CompletedAnalysis {
  id: string;
  company: string;
  completedAt: Date;
  status: "success" | "error";
  totalUpdates: number;
  updates: UpdateItem[];
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

export const RealTimeUpdates: React.FC<RealTimeUpdatesProps> = ({
  company,
  userNotes,
  extractionSchema,
  onComplete,
  onError,
  isActive,
}) => {
  const [updates, setUpdates] = useState<UpdateItem[]>([]);
  const [completedAnalyses, setCompletedAnalyses] = useState<
    CompletedAnalysis[]
  >([]);
  const [isConnected, setIsConnected] = useState(false);
  const [isComplete, setIsComplete] = useState(false);
  const [hoveredUpdate, setHoveredUpdate] = useState<UpdateItem | null>(null);
  const [tooltipPosition, setTooltipPosition] = useState({ x: 0, y: 0 });
  const eventSourceRef = useRef<EventSource | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  // Reset state when starting new analysis
  useEffect(() => {
    if (isActive && company) {
      setUpdates([]);
      setIsConnected(false);
      setIsComplete(false);
      startStream();
    }
  }, [company, userNotes, isActive]);

  const startStream = () => {
    if (!company) return;

    const token = authService.getToken();
    if (!token) {
      addUpdate("Authentication required. Please login again.", "error");
      onError("Authentication required. Please login again.");
      return;
    }

    const params = new URLSearchParams();
    params.set("company", company);
    params.set("token", token);
    if (userNotes) params.set("user_notes", userNotes);
    // Don't encode extraction_schema here
    if (extractionSchema) {
      params.set("extraction_schema", JSON.stringify(extractionSchema));
    }

    const eventSource = new EventSource(
      `${API_BASE_URL}/api/stream?${params.toString()}`
    );

    eventSourceRef.current = eventSource;

    eventSource.onopen = () => {
      setIsConnected(true);
      addUpdate(`Started analyzing ${company}`, "success");
    };

    eventSource.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);

        if (data.type === "update") {
          addUpdate(data.message, "info", data);
        } else if (data.type === "warning") {
          addUpdate(data.message, "warning", data);
        } else if (data.type === "success") {
          addUpdate(data.message || "done", "success", data);
        } else if (data.type === "complete") {
          addUpdate("Research completed successfully!", "success", data);
          setIsComplete(true);
          onComplete(data.result);
          eventSource.close();
        } else if (data.type === "error") {
          addUpdate(data.message, "error", data);
          setIsComplete(true);
          onError(data.message);
          eventSource.close();
        }
      } catch (error) {
        addUpdate(event.data, "info", { raw: event.data });
      }
    };

    eventSource.onerror = () => {
      setIsConnected(false);
      addUpdate("Connection lost. Retrying...", "warning");
    };
  };

  useEffect(() => {
    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
      }
    };
  }, []);

  const addUpdate = (message: string, type: UpdateItem["type"], data?: any) => {
    const newUpdate: UpdateItem = {
      id: Date.now().toString(),
      message,
      timestamp: new Date(),
      type,
      company,
      data,
    };

    setUpdates((prev) => [...prev, newUpdate]);

    setTimeout(() => {
      if (scrollRef.current) {
        scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
      }
    }, 100);
  };

  const getUpdateIcon = (type: UpdateItem["type"]) => {
    switch (type) {
      case "success":
        return <CheckCircle className="h-4 w-4 text-green-500" />;
      case "warning":
        return <AlertCircle className="h-4 w-4 text-yellow-500" />;
      case "error":
        return <AlertCircle className="h-4 w-4 text-red-500" />;
      default:
        return <Clock className="h-4 w-4 text-blue-500" />;
    }
  };

  const getUpdateBadgeVariant = (type: UpdateItem["type"]) => {
    switch (type) {
      case "success":
        return "default";
      case "warning":
        return "secondary";
      case "error":
        return "destructive";
      default:
        return "outline";
    }
  };

  const renderUpdates = (updatesToRender: UpdateItem[]) =>
    updatesToRender.map((update) => (
      <div
        key={update.id}
        className="flex items-start gap-3 p-3 rounded-lg border bg-card/50 transition-all duration-200 hover:bg-card"
      >
        <div className="flex-shrink-0 mt-0.5">{getUpdateIcon(update.type)}</div>
        <div className="flex-1 min-w-0">
          <p className="text-sm text-foreground break-words">
            {update.message}
          </p>
          <div className="flex items-center gap-2 mt-1">
            <div className="relative group">
              <Badge
                variant={getUpdateBadgeVariant(update.type)}
                className={`text-xs relative ${
                  update.type === "info" ? "cursor-help" : ""
                }`}
                {...(update.type === "info"
                  ? {
                      onMouseEnter: (e) => {
                        const rect = e.currentTarget.getBoundingClientRect();
                        setTooltipPosition({
                          x: rect.left + rect.width / 2,
                          y: rect.top - 10,
                        });
                        setHoveredUpdate(update);
                      },
                      onMouseLeave: () => {
                        setHoveredUpdate(null);
                      },
                    }
                  : {})}
              >
                {update.type}
              </Badge>
            </div>
            <span className="text-xs text-muted-foreground">
              {update.timestamp.toLocaleTimeString("en-US", {
                hour12: false,
                hour: "2-digit",
                minute: "2-digit",
                second: "2-digit",
              })}
              .{update.timestamp.getMilliseconds().toString().padStart(3, "0")}
            </span>
          </div>
        </div>
      </div>
    ));

  return (
    <>
      <Card className="h-full flex flex-col">
        <CardHeader className="pb-4">
          <CardTitle className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Zap className="h-5 w-5" />
              Analysis Timeline
            </div>
            <div className="flex items-center gap-2">
              {isActive && (
                <Badge variant={isConnected ? "default" : "secondary"}>
                  {isConnected ? "Connected" : "Initiating..."}
                </Badge>
              )}
              {isComplete && (
                <Badge variant="default" className="bg-green-500">
                  Complete
                </Badge>
              )}
            </div>
          </CardTitle>
        </CardHeader>

        <CardContent className="flex-1 flex flex-col overflow-hidden">
          <div
            ref={scrollRef}
            className="flex-1 space-y-3 overflow-y-auto pr-2"
          >
            {/* Analysis Timeline - Always show if there are updates */}
            {updates.length > 0 && (
              <div className="space-y-3">
                <div className="flex items-center gap-2 mb-4">
                  <div className="h-2 w-2 bg-blue-500 rounded-full animate-pulse"></div>
                  <span className="text-sm font-medium">
                    Current: {company}
                  </span>
                </div>

                {renderUpdates(updates)}

                {!isComplete && isConnected && (
                  <div className="mt-4 flex items-center gap-2 text-sm text-muted-foreground">
                    <div className="animate-spin rounded-full h-3 w-3 border-b border-primary"></div>
                    <span>Researching {company}...</span>
                  </div>
                )}
              </div>
            )}

            {/* Empty State */}
            {updates.length === 0 && (
              <div className="flex-1 flex items-center justify-center">
                <div className="text-center">
                  <History className="h-8 w-8 text-muted-foreground mx-auto mb-2" />
                  <p className="text-sm text-muted-foreground">
                    No analyses yet. Start by entering a company name.
                  </p>
                </div>
              </div>
            )}
          </div>
        </CardContent>
      </Card>
      {hoveredUpdate &&
        createPortal(
          <div
            className="fixed z-[9999] px-3 py-2 bg-gray-900 text-white text-xs rounded-lg shadow-lg max-w-sm min-w-48"
            style={{
              left: tooltipPosition.x,
              top: tooltipPosition.y,
              transform: "translateX(-50%) translateY(-100%)",
            }}
          >
            <div className="mb-1 font-medium break-words">
              {hoveredUpdate.message}
            </div>
            {hoveredUpdate.data &&
              Object.keys(hoveredUpdate.data).length > 0 && (
                <div className="space-y-1">
                  {Object.entries(hoveredUpdate.data)
                    .filter(
                      ([key]) => !["type", "timestamp", "message"].includes(key)
                    )
                    .map(([key, value]) => (
                      <div key={key} className="text-xs">
                        <span className="font-medium">{key}:</span>{" "}
                        <span className="break-words whitespace-normal">
                          {typeof value === "object"
                            ? JSON.stringify(value)
                            : String(value)}
                        </span>
                      </div>
                    ))}
                </div>
              )}
            <div className="absolute top-full left-1/2 transform -translate-x-1/2 w-0 h-0 border-l-4 border-r-4 border-t-4 border-transparent border-t-gray-900"></div>
          </div>,
          document.body
        )}
    </>
  );
};
