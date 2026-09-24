export interface FieldError {
  field: string;
  message: string;
}

/** RFC 9457 Problem Details as produced by the backend. */
export interface ProblemDetails {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  code?: string;
  errors?: FieldError[];
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly detail: string | undefined;
  readonly fieldErrors: FieldError[];

  constructor(status: number, code: string, detail?: string, fieldErrors: FieldError[] = []) {
    super(detail ?? code);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.detail = detail;
    this.fieldErrors = fieldErrors;
  }

  static fromProblem(status: number, problem: ProblemDetails | null): ApiError {
    return new ApiError(
      status,
      problem?.code ?? defaultCode(status),
      problem?.detail,
      Array.isArray(problem?.errors) ? problem.errors : [],
    );
  }

  static network(timedOut: boolean): ApiError {
    return new ApiError(0, timedOut ? 'TIMEOUT' : 'NETWORK_ERROR');
  }

  get isNetwork(): boolean {
    return this.status === 0;
  }
}

function defaultCode(status: number): string {
  switch (status) {
    case 400:
      return 'BAD_REQUEST';
    case 401:
      return 'UNAUTHENTICATED';
    case 403:
      return 'FORBIDDEN';
    case 404:
      return 'NOT_FOUND';
    case 409:
      return 'CONFLICT';
    case 422:
      return 'UNPROCESSABLE';
    default:
      return status >= 500 ? 'INTERNAL_ERROR' : 'REQUEST_ERROR';
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}
