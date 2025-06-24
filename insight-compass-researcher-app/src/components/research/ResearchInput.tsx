
import React, { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { CompanyResearchRequest } from '@/types/research';
import { Search, AlertCircle, CheckCircle, ArrowDown } from 'lucide-react';

interface ResearchInputProps {
  onResearch: (request: CompanyResearchRequest) => void;
  isLoading: boolean;
}

const SCHEMA_OPTIONS = {
  simple: `{"founded_year": "int", "headquarters": "str", "industry": "str"}`,
  detailed: `{
  "type": "object",
  "properties": {
    "title": { "type": "string" },
    "company_name": { "type": "string" },
    "company_size": { "type": "integer" },
    "founding_year": { "type": "integer" },
    "founder_names": { "type": "string" },
    "product_description": { "type": "string" },
    "funding_summary": { "type": "string" },
    "controversies": { "type": "string" },
    "acquisitions": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "company": { "type": "string" },
          "year": { "type": "integer" }
        },
        "required": ["company", "year"]
      }
    }
  }
}`
};

export const ResearchInput: React.FC<ResearchInputProps> = ({ onResearch, isLoading }) => {
  const [company, setCompany] = useState('');
  const [userNotes, setUserNotes] = useState('');
  const [extractionSchema, setExtractionSchema] = useState('');
  const [schemaError, setSchemaError] = useState<string>('');
  const [isValidSchema, setIsValidSchema] = useState<boolean>(true);

  const validateSchema = (schema: string) => {
    if (!schema.trim()) {
      setSchemaError('');
      setIsValidSchema(true);
      return true;
    }

    try {
      const parsed = JSON.parse(schema);
      if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
        setSchemaError('Schema must be a valid JSON object');
        setIsValidSchema(false);
        return false;
      }
      setSchemaError('');
      setIsValidSchema(true);
      return true;
    } catch (error) {
      setSchemaError('Invalid JSON format');
      setIsValidSchema(false);
      return false;
    }
  };

  const handleSchemaChange = (value: string) => {
    setExtractionSchema(value);
    validateSchema(value);
  };

  const insertSchema = (schemaType: 'simple' | 'detailed') => {
    const schema = SCHEMA_OPTIONS[schemaType];
    setExtractionSchema(schema);
    validateSchema(schema);
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!company.trim()) return;

    let parsedSchema;
    if (extractionSchema.trim()) {
      if (!validateSchema(extractionSchema)) {
        return; // Don't submit if schema is invalid
      }
      try {
        parsedSchema = JSON.parse(extractionSchema);
      } catch (error) {
        return; // This shouldn't happen due to validation, but just in case
      }
    }

    onResearch({
      company: company.trim(),
      extraction_schema: parsedSchema,
      user_notes: userNotes.trim() || undefined
    });
  };

  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Search className="h-5 w-5" />
          Company Research
        </CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="company">Company Name *</Label>
            <Input
              id="company"
              value={company}
              onChange={(e) => setCompany(e.target.value)}
              placeholder="e.g., Microsoft, Gulf Researcher, Apple, Google"
              required
            />
          </div>
          
          <div className="space-y-2">
            <Label htmlFor="userNotes">User Notes (Optional)</Label>
            <Textarea
              id="userNotes"
              value={userNotes}
              onChange={(e) => setUserNotes(e.target.value)}
              placeholder="Include details about recent partnerships, specific focus areas..."
              rows={3}
            />
          </div>
          
          <div className="space-y-2">
            <Label htmlFor="extractionSchema" className="flex items-center gap-2">
              Extraction Schema (Optional JSON)
              {extractionSchema.trim() && (
                isValidSchema ? (
                  <CheckCircle className="h-4 w-4 text-green-500" />
                ) : (
                  <AlertCircle className="h-4 w-4 text-red-500" />
                )
              )}
            </Label>
            
            {/* Schema suggestion buttons */}
            <div className="flex flex-col gap-2 p-3 bg-muted/50 rounded-md">
              <p className="text-xs text-muted-foreground font-medium">Quick Templates:</p>
              <div className="flex flex-col sm:flex-row gap-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => insertSchema('simple')}
                  className="flex items-center gap-1 text-xs"
                >
                  <ArrowDown className="h-3 w-3" />
                  Simple Schema
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => insertSchema('detailed')}
                  className="flex items-center gap-1 text-xs"
                >
                  <ArrowDown className="h-3 w-3" />
                  Detailed Schema
                </Button>
              </div>
            </div>
            
            <Textarea
              id="extractionSchema"
              value={extractionSchema}
              onChange={(e) => handleSchemaChange(e.target.value)}
              placeholder='{"founded_year": "int", "headquarters": "str", "industry": "str"}'
              rows={10}
              className={`font-mono text-sm ${
                schemaError ? 'border-red-500 focus-visible:ring-red-500' : ''
              }`}
            />
            {schemaError && (
              <p className="text-sm text-red-500 flex items-center gap-1">
                <AlertCircle className="h-3 w-3" />
                {schemaError}
              </p>
            )}
          </div>
          
          <Button 
            type="submit" 
            className="w-full" 
            disabled={isLoading || !company.trim() || !isValidSchema}
          >
            {isLoading ? 'Analyzing...' : 'Analyze Company'}
          </Button>
          
          <div className="text-xs text-muted-foreground p-2 bg-muted rounded">
            <strong>Example:</strong> Try "Microsoft" with the extraction schema above.
          </div>
        </form>
      </CardContent>
    </Card>
  );
};
